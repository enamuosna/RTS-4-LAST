package sn.rts.caisse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.exception.BusinessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Sauvegarde / restauration de la base PostgreSQL.
 *
 * <p>Utilise les binaires {@code pg_dump} et {@code psql} fournis par le
 * paquet Alpine {@code postgresql-client} installé dans l'image Docker
 * backend (cf. {@code rts-caisse-docker/backend/Dockerfile}).</p>
 *
 * <p><b>Sécurité</b> : ces opérations sont réservées aux ADMIN via le
 * {@code @PreAuthorize} du controller. Toute exécution est tracée dans le
 * journal d'audit.</p>
 */
@Slf4j
@Service
public class BackupService {

    /** Nom du service Docker postgres (résolu par le réseau interne). */
    private static final String DB_HOST = "postgres";
    private static final String DB_PORT = "5432";

    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final AuditService auditService;

    public BackupService(
            @Value("${POSTGRES_DB:rts_caisse_db}") String dbName,
            @Value("${POSTGRES_USER:postgres}") String dbUser,
            @Value("${POSTGRES_PASSWORD:}") String dbPassword,
            AuditService auditService) {
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.auditService = auditService;
    }

    // ==================================================================
    //  EXPORT
    // ==================================================================

    /**
     * Lance {@code pg_dump} et redirige sa sortie vers {@code out}.
     * Le dump est au format SQL plain text avec instructions {@code DROP IF EXISTS}
     * et {@code CREATE}, donc rejouable avec {@code psql} sur une BDD vide ou
     * existante (les objets existants sont remplacés).
     */
    public void exporter(OutputStream out, String loginAdmin) {
        log.info("Export BDD demandé par {}", loginAdmin);
        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "--host=" + DB_HOST,
                "--port=" + DB_PORT,
                "--username=" + dbUser,
                "--dbname=" + dbName,
                "--no-owner",
                "--no-privileges",
                "--clean",
                "--if-exists",
                "--encoding=UTF8"
        );
        pb.redirectErrorStream(false);
        pb.environment().put("PGPASSWORD", dbPassword);

        try {
            Process p = pb.start();

            // Drain stderr en parallèle pour qu'il ne bloque pas le buffer OS
            Thread errReader = new Thread(() -> drainStream(p.getErrorStream(), true));
            errReader.setDaemon(true);
            errReader.start();

            try (InputStream in = p.getInputStream()) {
                in.transferTo(out);
                out.flush();
            }

            int exit = p.waitFor();
            errReader.join(2000);

            if (exit != 0) {
                auditService.logFailure(AuditAction.EXPORTER_BDD, "Database", null,
                        "user=" + loginAdmin,
                        "pg_dump exit=" + exit);
                throw new BusinessException(
                        "Échec de l'export (pg_dump a retourné " + exit + ").");
            }

            auditService.logSuccess(AuditAction.EXPORTER_BDD, "Database", null,
                    dbName, "Export BDD par " + loginAdmin);
            log.info("Export BDD terminé avec succès pour {}", loginAdmin);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Échec export BDD", e);
            throw new BusinessException(
                    "Impossible d'exécuter pg_dump : " + e.getMessage());
        }
    }

    // ==================================================================
    //  IMPORT
    // ==================================================================

    /**
     * Restaure la BDD depuis un dump SQL. <b>Écrase les données existantes</b>
     * (le dump contient des {@code DROP IF EXISTS}).
     *
     * <p>Limitations connues :
     * <ul>
     *   <li>Le backend doit être redémarré après import pour réinitialiser
     *       le pool JPA et invalider les caches Hibernate.</li>
     *   <li>L'historique Flyway est restauré tel quel : aucune migration
     *       supplémentaire n'est jouée sur le dump.</li>
     * </ul>
     */
    public ResultatImport importer(InputStream sqlStream, String loginAdmin) {
        log.warn("Restauration BDD demandée par {} — les données existantes "
                + "vont être ÉCRASÉES", loginAdmin);

        Path temp = null;
        try {
            temp = Files.createTempFile("rts-restore-", ".sql");
            Files.copy(sqlStream, temp, StandardCopyOption.REPLACE_EXISTING);
            long taille = Files.size(temp);

            ProcessBuilder pb = new ProcessBuilder(
                    "psql",
                    "--host=" + DB_HOST,
                    "--port=" + DB_PORT,
                    "--username=" + dbUser,
                    "--dbname=" + dbName,
                    "--single-transaction",
                    "--set", "ON_ERROR_STOP=on",
                    "--file=" + temp
            );
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            List<String> dernieresLignes = lireDernieresLignes(p.getInputStream(), 30);
            int exit = p.waitFor();

            if (exit != 0) {
                String contexte = String.join("\n", dernieresLignes);
                auditService.logFailure(AuditAction.IMPORTER_BDD, "Database", null,
                        "user=" + loginAdmin + " taille=" + taille,
                        "psql exit=" + exit + " : " + contexte);
                throw new BusinessException(
                        "Échec de la restauration (psql exit=" + exit + ").\n"
                                + "Dernières lignes :\n" + contexte);
            }

            auditService.logSuccess(AuditAction.IMPORTER_BDD, "Database", null,
                    dbName, "Restauration BDD par " + loginAdmin
                            + " (taille dump=" + taille + " octets)");
            log.info("Restauration BDD terminée avec succès pour {} ({} octets)",
                    loginAdmin, taille);

            return new ResultatImport(taille, dernieresLignes);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Échec import BDD", e);
            throw new BusinessException(
                    "Impossible d'exécuter psql : " + e.getMessage());
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); }
                catch (IOException e) { log.warn("Suppression du temp impossible : {}", e.getMessage()); }
            }
        }
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private void drainStream(InputStream in, boolean asError) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (asError) log.warn("[pg_dump] {}", line);
                else         log.info("[psql] {}", line);
            }
        } catch (IOException e) {
            // EOF normal lors de la fin du process
        }
    }

    private List<String> lireDernieresLignes(InputStream in, int max) throws IOException {
        java.util.LinkedList<String> buffer = new java.util.LinkedList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                buffer.add(line);
                if (buffer.size() > max) buffer.removeFirst();
                log.info("[psql] {}", line);
            }
        }
        return new java.util.ArrayList<>(buffer);
    }

    public record ResultatImport(long tailleOctets, List<String> dernieresLignesPsql) {}
}

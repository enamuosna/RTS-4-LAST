package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.security.BackupRateLimitService;
import sn.rts.caisse.security.TooManyBackupRequestsException;
import sn.rts.caisse.service.BackupService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sauvegarde et restauration de la base PostgreSQL.
 *
 * <ul>
 *   <li><b>GET</b> /api/backup/export — télécharge un dump SQL (ADMIN)</li>
 *   <li><b>POST</b> /api/backup/import — restaure depuis un dump SQL (ADMIN)</li>
 * </ul>
 *
 * <p>L'import écrase les données existantes. Une confirmation explicite côté
 * UI (saisie du mot {@code RESTORE}) est requise avant que le frontend
 * appelle cet endpoint.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Tag(name = "Sauvegarde", description = "Export / import de la base PostgreSQL (ADMIN)")
public class BackupController {

    private static final DateTimeFormatter NOM_FICHIER_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Taille max du dump à importer : 100 Mo (ajustable). */
    private static final long TAILLE_MAX_OCTETS = 100L * 1024 * 1024;

    private final BackupService backupService;
    private final BackupRateLimitService backupRateLimit;

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Télécharge un dump SQL complet de la BDD",
               description = "Rate limit : 1 operation backup (export ou import) "
                       + "par utilisateur toutes les 60 minutes. 429 + Retry-After "
                       + "au-dela.")
    public ResponseEntity<ByteArrayResource> exporter(Authentication authentication) {
        String login = authentication.getName();

        // Rate limit : un export complet de la BDD est une operation
        // ponctuelle, jamais recurrente. Si un meme admin enchaine 5 exports
        // en 10 min, c'est anormal -> on bloque pour empecher l'exfiltration
        // a grande vitesse si le compte est compromis.
        backupRateLimit.checkAllowed(login).ifPresent(secondsToWait -> {
            throw new TooManyBackupRequestsException(secondsToWait);
        });

        String nomFichier = "rts-caisse-backup-"
                + LocalDateTime.now().format(NOM_FICHIER_FMT) + ".sql";

        // Génération bufferisée en mémoire : la réponse est renvoyée d'un
        // bloc avec Content-Length connu. Évite les problèmes de protocole
        // (HTTP/3 / QUIC + StreamingResponseBody = ERR_QUIC_PROTOCOL_ERROR
        // sur Edge derrière Caddy).
        byte[] dump = backupService.exporter(login);
        backupRateLimit.recordOperation(login);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment()
                        .filename(nomFichier).build());
        headers.setContentType(MediaType.parseMediaType("application/sql"));
        headers.setContentLength(dump.length);
        headers.setCacheControl("no-store, no-cache, must-revalidate");

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ByteArrayResource(dump));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restaure la BDD depuis un dump SQL (DROP / CREATE intégrés)",
               description = "DANGER : écrase toutes les données existantes. "
                       + "Le backend doit être redémarré après import pour réinitialiser "
                       + "le pool JPA et invalider les caches Hibernate.")
    public ResponseEntity<RapportImport> importer(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String login = authentication.getName();

        // Meme rate limit que pour l'export : 1 operation backup max par
        // heure pour cet utilisateur. Import et export partagent le meme
        // compteur car ce sont deux operations egalement sensibles.
        backupRateLimit.checkAllowed(login).ifPresent(secondsToWait -> {
            throw new TooManyBackupRequestsException(secondsToWait);
        });

        valider(file);
        try {
            BackupService.ResultatImport r = backupService.importer(
                    file.getInputStream(), login);
            backupRateLimit.recordOperation(login);
            return ResponseEntity.ok(new RapportImport(
                    true,
                    r.tailleOctets(),
                    "Restauration réussie. Il est fortement recommandé de "
                            + "redémarrer le backend pour appliquer pleinement la "
                            + "nouvelle base.",
                    r.dernieresLignesPsql()));
        } catch (java.io.IOException e) {
            throw new BusinessException("Lecture du fichier impossible : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Validation
    // ==================================================================

    private void valider(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Aucun fichier reçu.");
        }
        if (file.getSize() > TAILLE_MAX_OCTETS) {
            throw new BusinessException(
                    "Fichier trop volumineux (max 100 Mo, reçu "
                            + (file.getSize() / 1024 / 1024) + " Mo).");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".sql")) {
            throw new BusinessException(
                    "Extension attendue : .sql (fichier reçu : " + name + ").");
        }
    }

    public record RapportImport(boolean succes,
                                long tailleOctets,
                                String message,
                                java.util.List<String> dernieresLignesPsql) {}
}

package sn.rts.caisse.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.exception.ResourceNotFoundException;

import java.time.LocalDateTime;

/**
 * Endpoints de consultation du journal d'audit.
 *
 * <p><b>Sécurité</b> : réservé aux administrateurs (ROLE_ADMIN).
 * Les superviseurs n'y ont pas accès car les logs peuvent contenir
 * des informations sensibles (IP, user-agent, montants…).</p>
 *
 * <p>Toutes les requêtes paginées renvoient la dernière action en haut
 * (tri descendant sur {@code createdAt}). Le client peut surcharger
 * en passant {@code ?sort=createdAt,asc}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/audit/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit", description = "Consultation du journal d'audit système")
public class AuditLogController {

    /** Plafond du nombre de lignes par page pour éviter une charge excessive. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Recherche paginée et filtrée du journal d'audit")
    public ResponseEntity<Page<AuditLogResponse>> rechercher(
            @Parameter(description = "Type d'action exact (ex. LOGIN_SUCCESS)")
            @RequestParam(required = false) AuditAction action,

            @Parameter(description = "ID utilisateur auteur de l'action")
            @RequestParam(required = false) Long userId,

            @Parameter(description = "Type d'entité affectée (ex. OperationCaisse)")
            @RequestParam(required = false) String entityType,

            @Parameter(description = "ID de l'entité affectée")
            @RequestParam(required = false) Long entityId,

            @Parameter(description = "Filtrer succès / échec")
            @RequestParam(required = false) Boolean success,

            @Parameter(description = "Borne inférieure (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,

            @Parameter(description = "Borne supérieure (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,

            @Parameter(description = "Numéro de page (0-indexé)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Taille de la page (max " + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "50") int size,

            @Parameter(description = "Champ et sens de tri (ex. createdAt,desc)")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        try {
            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            Pageable pageable = PageRequest.of(
                    Math.max(page, 0),
                    safeSize,
                    parseSort(sort));

            // Build dynamic specification (ignore les filtres null)
            Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                    action, userId, entityType, entityId, success, dateFrom, dateTo);

            // findAll(spec, pageable) provient de JpaSpecificationExecutor
            Page<AuditLogResponse> result = repository
                    .findAll(spec, pageable)
                    .map(AuditLogResponse::from);

            // Audit-meta : qui consulte les logs (après le SELECT pour ne
            // pas masquer l'erreur si la requete principale plante)
            auditService.logSuccess(
                    AuditAction.CONSULTER_AUDIT_LOG,
                    "AuditLog", null, null,
                    "page=" + page + " size=" + safeSize
                            + " resultats=" + result.getNumberOfElements()
                            + (action != null ? " action=" + action : "")
                            + (userId != null ? " userId=" + userId : ""));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Erreur lors de la consultation des logs d'audit", e);
            auditService.logFailure(
                    AuditAction.CONSULTER_AUDIT_LOG,
                    "AuditLog", null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une entree d'audit")
    public ResponseEntity<AuditLogResponse> obtenir(@PathVariable Long id) {
        AuditLog entry = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AuditLog", id));
        return ResponseEntity.ok(AuditLogResponse.from(entry));
    }

    /**
     * Export CSV du journal d'audit. Respecte les memes filtres que la
     * consultation paginee. Encodage UTF-8 avec BOM pour qu'Excel ouvre
     * correctement les caracteres accentues sans manipulation manuelle.
     *
     * <p>Volontairement non pagine : on stream tout ce qui matche les
     * filtres. C'est l'admin qui est responsable de filtrer assez
     * etroitement (par exemple sur 1 mois) pour ne pas exporter 100k
     * lignes inutilement.</p>
     */
    @GetMapping(value = "/export.csv",
            produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Exporte le journal d'audit filtre au format CSV")
    public ResponseEntity<byte[]> exporterCsv(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {

        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                action, userId, entityType, entityId, success, dateFrom, dateTo);
        // Tri descendant par date pour avoir les plus recents en haut.
        java.util.List<AuditLog> logs = repository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        StringBuilder csv = new StringBuilder();
        // BOM UTF-8 pour Excel
        csv.append('﻿');
        csv.append("Date;Action;UserId;Login;Matricule;NomComplet;Role;");
        csv.append("EntityType;EntityId;EntityLabel;Success;IP;UserAgent;");
        csv.append("HttpMethod;HttpPath;ErrorMessage;Details\n");
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AuditLog l : logs) {
            csv.append(l.getCreatedAt() != null ? l.getCreatedAt().format(fmt) : "");
            csv.append(';').append(escape(l.getAction()));
            csv.append(';').append(escape(l.getUserId()));
            csv.append(';').append(escape(l.getUserLogin()));
            csv.append(';').append(escape(l.getUserMatricule()));
            csv.append(';').append(escape(l.getUserNomComplet()));
            csv.append(';').append(escape(l.getUserRole()));
            csv.append(';').append(escape(l.getEntityType()));
            csv.append(';').append(escape(l.getEntityId()));
            csv.append(';').append(escape(l.getEntityLabel()));
            csv.append(';').append(l.isSuccess() ? "OUI" : "NON");
            csv.append(';').append(escape(l.getIpAddress()));
            csv.append(';').append(escape(l.getUserAgent()));
            csv.append(';').append(escape(l.getHttpMethod()));
            csv.append(';').append(escape(l.getHttpPath()));
            csv.append(';').append(escape(l.getErrorMessage()));
            csv.append(';').append(escape(l.getDetails()));
            csv.append('\n');
        }
        byte[] body = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = "journal-audit-"
                + java.time.LocalDate.now() + ".csv";

        auditService.logSuccess(
                AuditAction.EXPORTER_AUDIT_LOG,
                "AuditLog", null, null,
                "lignes=" + logs.size() + " filename=" + filename);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        "text/csv; charset=UTF-8")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /**
     * Purge les logs d'audit anterieurs a N jours (defaut 90). Reservee aux
     * ADMIN. L'action de purge elle-meme est tracee dans le journal pour
     * conserver une trace de qui a vide quoi et quand.
     *
     * @param joursConservation nombre de jours d'historique a conserver (min 7)
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/purge")
    @Operation(summary = "Purge les logs anterieurs a N jours (defaut 90)")
    public ResponseEntity<java.util.Map<String, Object>> purger(
            @RequestParam(defaultValue = "90") int joursConservation) {
        // Garde minimum 7 jours pour eviter une purge totale accidentelle.
        int joursSafe = Math.max(joursConservation, 7);
        LocalDateTime seuil = LocalDateTime.now().minusDays(joursSafe);

        int supprimees = repository.deleteOlderThan(seuil);

        log.warn("Purge audit log : {} entrees supprimees (anterieures a {})",
                supprimees, seuil);
        auditService.logSuccess(
                AuditAction.PURGER_AUDIT_LOG,
                "AuditLog", null, null,
                "joursConservation=" + joursSafe + " seuil=" + seuil
                        + " supprimees=" + supprimees);

        return ResponseEntity.ok(java.util.Map.of(
                "supprimees", supprimees,
                "joursConservation", joursSafe,
                "seuilDate", seuil.toString()
        ));
    }

    /** Echappe un champ pour le format CSV : doubles guillemets pour
     *  encadrer les valeurs contenant point-virgule, guillemet ou saut
     *  de ligne. Les guillemets internes sont doubles. */
    private static String escape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        boolean needsQuotes = s.contains(";") || s.contains("\"")
                || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static Sort parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = raw.split(",");
        String prop = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(dir, prop.isBlank() ? "createdAt" : prop);
    }
}

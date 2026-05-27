package sn.rts.caisse.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.model.Utilisateur;

import java.util.EnumSet;
import java.util.Set;

/**
 * Endpoint dédié à la remontée des événements émis par le <b>client lourd</b>
 * (guichet JavaFX) vers le journal d'audit central.
 *
 * <p>Permet de centraliser dans la même table {@code audit_logs} :
 * <ul>
 *   <li>les actions REST classiques (déjà tracées par les services métier) ;</li>
 *   <li>les événements purement locaux du desktop qui ne déclenchent pas
 *       d'appel REST métier : démarrage / arrêt de l'app, impression d'un
 *       reçu sur imprimante, export d'un fichier PNG/PDF, échec de ping
 *       serveur en mode dégradé.</li>
 * </ul>
 *
 * <h2>Sécurité</h2>
 * <ul>
 *   <li>L'endpoint est <b>ouvert sans authentification</b> (cf.
 *       {@code SecurityConfig}) car certains événements (démarrage avant
 *       connexion, échec ping serveur) n'ont pas encore de JWT.</li>
 *   <li>L'identité de l'auteur n'est <b>jamais</b> lue depuis le payload :
 *       elle est extraite du JWT si présent. Cela empêche un poste de
 *       forger une entrée d'audit au nom d'un autre utilisateur.</li>
 *   <li>Une <b>whitelist</b> d'actions empêche un client compromis
 *       d'écrire des entrées de type {@code LOGIN_SUCCESS},
 *       {@code CREER_OPERATION}, etc. depuis cet endpoint.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/audit/client-events")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Remontée des événements du client lourd")
public class ClientAuditEventController {

    /**
     * Actions autorisées depuis le client lourd. Toute autre valeur est
     * rejetée par 400 pour empêcher un client compromis d'écrire un
     * LOGIN_SUCCESS ou CREER_OPERATION non vérifié.
     */
    private static final Set<AuditAction> ACTIONS_AUTORISEES = EnumSet.of(
            AuditAction.DEMARRER_APP_GUICHET,
            AuditAction.ARRETER_APP_GUICHET,
            AuditAction.ECHEC_CONNEXION_SERVEUR,
            AuditAction.IMPRIMER_RECU,
            AuditAction.EXPORTER_RECU_FICHIER
    );

    private final AuditService auditService;

    @PostMapping
    @Operation(summary = "Remonte un événement émis par le client lourd au journal d'audit")
    public ResponseEntity<Void> remonter(@Valid @RequestBody ClientAuditEventRequest req) {
        if (req == null || req.getAction() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!ACTIONS_AUTORISEES.contains(req.getAction())) {
            log.warn("Action client refusée (hors whitelist) : {}", req.getAction());
            return ResponseEntity.badRequest().build();
        }

        Utilisateur current = AuditContextHelper.currentUser();
        String details = enrichirDetails(req);

        if (req.isSuccess()) {
            if (current != null) {
                auditService.logSuccess(req.getAction(),
                        req.getEntityType(), req.getEntityId(), req.getEntityLabel(),
                        details);
            } else {
                // Pas de JWT : on passe par logForUser(null, …) pour conserver
                // quand même l'IP, le User-Agent et l'horodatage.
                auditService.logForUser(req.getAction(), null,
                        req.getEntityType(), req.getEntityId(), req.getEntityLabel(),
                        true, null, details);
            }
        } else {
            if (current != null) {
                auditService.logFailure(req.getAction(),
                        req.getEntityType(), req.getEntityId(), req.getEntityLabel(),
                        req.getErrorMessage());
            } else {
                auditService.logForUser(req.getAction(), null,
                        req.getEntityType(), req.getEntityId(), req.getEntityLabel(),
                        false, req.getErrorMessage(), details);
            }
        }
        return ResponseEntity.accepted().build();
    }

    /**
     * Concatène le hostname et la version du client lourd au champ details
     * pour conserver le contexte poste/version dans la même colonne.
     */
    private String enrichirDetails(ClientAuditEventRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getDetails() != null && !req.getDetails().isBlank()) {
            sb.append(req.getDetails());
        }
        String marker = " [client-lourd";
        boolean hasHost = req.getHostname() != null && !req.getHostname().isBlank();
        boolean hasVer  = req.getAppVersion() != null && !req.getAppVersion().isBlank();
        if (hasHost || hasVer) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(marker);
            if (hasHost) sb.append(" host=").append(req.getHostname());
            if (hasVer)  sb.append(" v=").append(req.getAppVersion());
            sb.append(']');
        } else if (sb.length() == 0) {
            sb.append("[client-lourd]");
        }
        return sb.toString();
    }
}

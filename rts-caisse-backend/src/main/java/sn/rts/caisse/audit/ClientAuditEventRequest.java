package sn.rts.caisse.audit;

import jakarta.validation.constraints.NotNull;

/**
 * Payload envoyé par le client lourd (guichet JavaFX) pour journaliser un
 * événement local qui ne transite pas par un endpoint REST métier
 * (démarrage / arrêt de l'app, impression réelle d'un reçu sur imprimante,
 * export d'un fichier PNG/PDF, échec du ping serveur en mode dégradé).
 *
 * <p>L'identité de l'auteur n'est jamais lue depuis ce payload : elle est
 * extraite côté serveur du JWT présent dans le header Authorization. Si
 * aucun JWT n'est fourni (cas du démarrage avant connexion), l'entrée est
 * écrite sans userId. Le champ {@link #hostname} reste libre pour permettre
 * d'identifier le poste émetteur.</p>
 */
public class ClientAuditEventRequest {

    /** Action auditée. Sera validée contre une whitelist côté serveur. */
    @NotNull
    private AuditAction action;

    /** {@code true} si l'événement représente un succès, {@code false} sinon. */
    private boolean success = true;

    /** Message d'erreur si {@link #success} est faux. */
    private String errorMessage;

    /** Détails libres (mode paiement, motif, chemin du fichier exporté…). */
    private String details;

    /** Type d'entité concernée (ex. "OperationCaisse") - optionnel. */
    private String entityType;

    /** ID de l'entité concernée - optionnel. */
    private Long entityId;

    /** Libellé lisible de l'entité (numéro de reçu, etc.) - optionnel. */
    private String entityLabel;

    /** Nom du poste émetteur, injecté dans le champ details côté serveur. */
    private String hostname;

    /** Version du client lourd (ex. "1.0.0") - injecté dans les details. */
    private String appVersion;

    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getEntityLabel() { return entityLabel; }
    public void setEntityLabel(String entityLabel) { this.entityLabel = entityLabel; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
}

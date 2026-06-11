package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

/**
 * Caisse physique (un guichet, un poste de paiement).
 * Exemples : "Guichet Accueil RTS Triangle Sud", "Caisse Régie Publicitaire".
 */
@Entity
@Table(name = "caisses",
        uniqueConstraints = @UniqueConstraint(name = "uk_caisse_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caisse extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique (ex : "CAI-01"). */
    @NotBlank
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String libelle;

    /** Localisation physique : siège RTS, annexe régionale, etc. */
    @Column(length = 150)
    private String emplacement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutCaisse statut = StatutCaisse.FERMEE;

    /**
     * Type(s) d'opération autorisé(s) sur cette caisse, fixé par l'ADMIN.
     * Par défaut {@link TypeOperationAutorise#TOUS} (caisse mixte) pour la
     * compatibilité avec les caisses existantes.
     *
     * <p>{@code columnDefinition} explicite (avec {@code DEFAULT 'TOUS'}) pour
     * (1) renseigner les lignes déjà en base lors de l'ajout de la colonne et
     * (2) empêcher Hibernate de générer un CHECK constraint figé — même
     * approche que sur {@code utilisateurs.role}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_operation_autorise", nullable = false,
            columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'TOUS'")
    @Builder.Default
    private TypeOperationAutorise typeOperationAutorise = TypeOperationAutorise.TOUS;

    /**
     * Solde théorique courant (fond de caisse + mouvements du jour).
     * Mis à jour transactionnellement par le service.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal soldeCourant = BigDecimal.ZERO;

    /** Agent actuellement affecté à cette caisse (peut être null si fermée). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caissier_id")
    private Utilisateur caissier;

    /**
     * Agent de recette rattaché à cette caisse. Peut modifier et réactiver
     * les opérations du caissier en cas d'erreur. Nullable : si non
     * affecté, seuls les ADMIN/SUPERVISEUR peuvent corriger.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_recette_id")
    private Utilisateur agentRecette;
}

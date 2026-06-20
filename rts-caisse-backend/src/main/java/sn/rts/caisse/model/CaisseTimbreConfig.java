package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuration du timbre fiscal <b>propre à une caisse</b>.
 *
 * <p>Chaque caisse possède (au plus) une ligne. L'administrateur définit, par
 * caisse : l'activation, le seuil, le taux, les modes/catégories concernés et
 * surtout le {@link ModeTimbre} :</p>
 * <ul>
 *   <li>{@link ModeTimbre#AUTO} : timbre calculé automatiquement selon ces règles.</li>
 *   <li>{@link ModeTimbre#MANUEL} : le caissier saisit le timbre à la main
 *       (optionnel) ; le calcul automatique est désactivé pour cette caisse.</li>
 * </ul>
 *
 * <p>Une caisse sans ligne dédiée utilise les valeurs par défaut (mode AUTO,
 * seuil 20 000, 1 % ESPECES) — voir {@code TimbreConfigService}.</p>
 */
@Entity
@Table(name = "caisse_timbre_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_caisse_timbre_caisse", columnNames = "caisse_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaisseTimbreConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caisse_id", nullable = false)
    private Long caisseId;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal seuil = new BigDecimal("20000");

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal pourcentage = new BigDecimal("1.00");

    @Column(name = "categories_json", columnDefinition = "TEXT")
    @Builder.Default
    private String categoriesJson = "[]";

    @Column(name = "modes_paiement_json", columnDefinition = "TEXT")
    @Builder.Default
    private String modesPaiementJson = "[\"ESPECES\"]";

    /** AUTO (calcul) ou MANUEL (saisie caissier). columnDefinition ALTER-safe. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    @Builder.Default
    private ModeTimbre mode = ModeTimbre.AUTO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;
}

package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuration personnalisable du timbre fiscal appliqué aux opérations
 * de caisse.
 *
 * <p><b>Singleton</b> : une seule ligne en base (id=1), modifiable uniquement
 * par les utilisateurs ADMIN via {@code /api/parametres/timbre}. Le guichet
 * (app desktop) lit cette configuration pour reproduire exactement le calcul
 * fait — et fait foi — côté backend.</p>
 *
 * <p>Le timbre s'applique à une opération lorsque :</p>
 * <ul>
 *   <li>{@link #actif} est vrai, ET</li>
 *   <li>le montant HT est &ge; {@link #seuil}, ET</li>
 *   <li>le mode de paiement fait partie des modes concernés (ou la liste est
 *       vide = tous les modes), ET</li>
 *   <li>la catégorie/produit fait partie des catégories concernées (ou la
 *       liste est vide = toutes les catégories).</li>
 * </ul>
 * Le montant du timbre vaut alors {@code montant * pourcentage / 100},
 * arrondi au FCFA.
 */
@Entity
@Table(name = "timbre_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimbreConfig {

    @Id
    private Long id;

    /** Active ou désactive globalement le timbre. */
    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    /** Seuil (inclusif) du montant HT, en FCFA, à partir duquel le timbre s'applique. */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal seuil = new BigDecimal("20000");

    /** Taux du timbre, en pourcentage du montant HT (ex : 1.00 = 1%). */
    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal pourcentage = new BigDecimal("1.00");

    /**
     * Catégories (produits) concernées, sous forme de tableau JSON d'IDs
     * (ex : {@code [1,4,7]}). Liste vide ou {@code []} = toutes les catégories.
     */
    @Column(name = "categories_json", columnDefinition = "TEXT")
    @Builder.Default
    private String categoriesJson = "[]";

    /**
     * Modes de paiement concernés, sous forme de tableau JSON de noms d'enum
     * (ex : {@code ["ESPECES","CHEQUE"]}). Liste vide ou {@code []} = tous les modes.
     */
    @Column(name = "modes_paiement_json", columnDefinition = "TEXT")
    @Builder.Default
    private String modesPaiementJson = "[\"ESPECES\"]";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;
}

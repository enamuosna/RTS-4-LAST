package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Paramètres de personnalisation du reçu PDF généré par {@code RecuPdfService}.
 *
 * <p><b>Singleton</b> : une seule ligne en base (id=1), modifiable uniquement
 * par les utilisateurs ADMIN via {@code /api/parametres/recu}.
 *
 * <p>Les champs scalaires couvrent les informations statiques, couleurs et
 * tailles. La structure {@code layout} (ordre + visibilité des sections) est
 * stockée en JSON dans {@link #layoutJson} et désérialisée par le service.
 */
@Entity
@Table(name = "parametres_recu")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParametresRecu {

    @Id
    private Long id;

    // ============= EN-TÊTE =============
    @Column(name = "logo_texte", length = 40)
    private String logoTexte;

    /** Logo uploadé (PNG, JPG, etc.). Prioritaire sur {@link #logoTexte} si présent. */
    @Lob
    @Column(name = "logo_image")
    private byte[] logoImage;

    @Column(name = "logo_content_type", length = 80)
    private String logoContentType;

    @Column(name = "raison_sociale", length = 200)
    private String raisonSociale;

    @Column(name = "sous_titre_entete", length = 200)
    private String sousTitreEntete;

    @Column(name = "ligne_legale", length = 200)
    private String ligneLegale;

    @Column(name = "capital", length = 100)
    private String capital;

    @Column(name = "adresse", length = 200)
    private String adresse;

    @Column(name = "telephone", length = 60)
    private String telephone;

    @Column(name = "boite_postale", length = 60)
    private String boitePostale;

    @Column(name = "ninea", length = 60)
    private String ninea;

    // ============= FOOTER =============
    @Column(name = "footer_ligne1", length = 200)
    private String footerLigne1;

    @Column(name = "footer_ligne2", length = 200)
    private String footerLigne2;

    @Column(name = "ville_signature", length = 100)
    private String villeSignature;

    // ============= COULEURS (hex #RRGGBB) =============
    @Column(name = "couleur_primaire", length = 7)
    private String couleurPrimaire;

    @Column(name = "couleur_accent", length = 7)
    private String couleurAccent;

    @Column(name = "couleur_texte", length = 7)
    private String couleurTexte;

    @Column(name = "couleur_texte_secondaire", length = 7)
    private String couleurTexteSecondaire;

    @Column(name = "couleur_success", length = 7)
    private String couleurSuccess;

    @Column(name = "couleur_danger", length = 7)
    private String couleurDanger;

    @Column(name = "couleur_fond_montant", length = 7)
    private String couleurFondMontant;

    // ============= TAILLES (pt) =============
    @Column(name = "taille_titre")
    private Integer tailleTitre;

    @Column(name = "taille_entete")
    private Integer tailleEntete;

    @Column(name = "taille_corps")
    private Integer tailleCorps;

    @Column(name = "taille_montant")
    private Integer tailleMontant;

    @Column(name = "taille_footer")
    private Integer tailleFooter;

    // ============= LAYOUT (JSON : ordre + visibilité des sections) =============
    /**
     * Format JSON :
     * {@code [{"id":"header","visible":true},{"id":"titre","visible":true}, ...]}
     */
    @Column(name = "layout_json", columnDefinition = "TEXT")
    private String layoutJson;

    // ============= MÉTADONNÉES =============
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;
}

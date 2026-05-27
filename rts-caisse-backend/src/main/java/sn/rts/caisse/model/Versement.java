package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Versement bancaire effectué depuis une caisse vers une banque.
 *
 * <p>Un versement est l'enregistrement d'un dépôt d'espèces (ou d'un
 * virement interne) effectué par le caissier ou l'agent de recette
 * pour transférer le contenu de la caisse vers un compte bancaire.</p>
 *
 * <p>Règles métier :</p>
 * <ul>
 *   <li>Toujours rattaché à une <b>caisse</b>.</li>
 *   <li>Optionnellement rattaché à un <b>journal</b> (utile pour exports
 *       et reporting par période).</li>
 *   <li>Toujours rattaché à une <b>banque</b> (destinataire du dépôt).</li>
 *   <li>Pièce jointe (bordereau de versement) <b>obligatoire</b> : scan
 *       PDF ou photo JPG/PNG, max 5 Mo. Stocké en base (bytea).</li>
 *   <li>Peut être créé avant, pendant ou après la clôture du journal.</li>
 * </ul>
 */
@Entity
@Table(name = "versements",
        indexes = {
                @Index(name = "idx_versement_caisse", columnList = "caisse_id"),
                @Index(name = "idx_versement_journal", columnList = "journal_id"),
                @Index(name = "idx_versement_date", columnList = "date_versement")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Versement extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Caisse d'où provient le versement (obligatoire). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    /** Journal optionnel : si renseigné, le versement apparaît dans le
     *  rapport Excel de ce journal. Sinon, il est seulement attaché à la
     *  caisse (consultable via la page Versements admin). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private JournalCaisse journal;

    /** Banque destinataire du dépôt. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banque_id", nullable = false)
    private Banque banque;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    /** Numéro du bordereau bancaire fourni par la banque. */
    @NotBlank
    @Size(max = 100)
    @Column(name = "numero_bordereau", nullable = false, length = 100)
    private String numeroBordereau;

    @NotNull
    @Column(name = "date_versement", nullable = false)
    private LocalDateTime dateVersement;

    /** Contenu binaire du bordereau (PDF ou image). */
    @NotNull
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "fichier_bordereau", nullable = false)
    private byte[] fichierBordereau;

    /** Nom original du fichier uploadé (pour l'extension et le téléchargement). */
    @NotBlank
    @Size(max = 255)
    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    /** Type MIME (application/pdf, image/jpeg, image/png). */
    @NotBlank
    @Size(max = 100)
    @Column(name = "type_mime", nullable = false, length = 100)
    private String typeMime;

    /** Taille du fichier en octets (denormalise pour eviter de charger
     *  fichierBordereau juste pour avoir la taille dans les listings). */
    @NotNull
    @Column(name = "taille_fichier", nullable = false)
    private Long tailleFichier;

    /** Utilisateur ayant enregistré le versement (caissier ou agent recette). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Utilisateur createdBy;

    /** Notes/commentaire libre (optionnel). */
    @Size(max = 500)
    @Column(length = 500)
    private String notes;
}

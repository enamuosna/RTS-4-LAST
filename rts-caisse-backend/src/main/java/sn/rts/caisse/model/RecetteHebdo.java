package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ventilation hebdomadaire des recettes d'une caisse, avec son circuit de
 * <b>double validation</b> (Contrôle 1 = Chef Unité Finances, Contrôle 2 =
 * Chef de Département).
 *
 * <p>Seul l'état de validation est persisté ici (qui a signé, quand, à quel
 * niveau). Les <b>montants</b> de la ventilation (par produit, totaux, fiches
 * de références, reversements) sont toujours <b>recalculés à la volée</b> à
 * partir des {@link OperationCaisse} et {@link Versement} de la période, afin
 * de rester exacts en cas de correction d'une opération avant validation.</p>
 *
 * <p>Une seule ventilation par {@code (caisse, dateDebut, dateFin)} : la
 * première consultation de la période crée la ligne au statut
 * {@link StatutRecette#BROUILLON}.</p>
 */
@Entity
@Table(name = "recette_hebdo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recette_hebdo_periode",
                columnNames = {"caisse_id", "date_debut", "date_fin"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecetteHebdo extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /**
     * Statut courant. {@code columnDefinition} explicite (et non {@code length})
     * pour empêcher Hibernate (profil docker, ddl-auto=update) de regénérer un
     * CHECK constraint figeant les valeurs : même approche que {@code Utilisateur.role}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL")
    @Builder.Default
    private StatutRecette statut = StatutRecette.BROUILLON;

    /** Signataire du Contrôle 1 (Chef Unité Finances). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "controle1_par_id")
    private Utilisateur controle1Par;

    @Column(name = "controle1_le")
    private LocalDateTime controle1Le;

    /** Signataire du Contrôle 2 (Chef de Département). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "controle2_par_id")
    private Utilisateur controle2Par;

    @Column(name = "controle2_le")
    private LocalDateTime controle2Le;

    @Column(length = 500)
    private String commentaire;
}

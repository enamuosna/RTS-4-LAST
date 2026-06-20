package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Un créneau de diffusion à l'antenne d'une opération de caisse : une date +
 * heure, et (optionnellement) une langue de diffusion. Une opération peut avoir
 * <b>plusieurs</b> créneaux (jours/heures différents). Le « nombre de diffusion »
 * d'une opération = nombre de créneaux.
 *
 * <p>Entité autonome (référence l'opération par {@code operationId}) pour rester
 * simple à gérer côté service (création / remplacement). La langue est figée
 * (snapshot {@code langueLibelle}) au moment de l'enregistrement.</p>
 */
@Entity
@Table(name = "operation_diffusion",
        indexes = @Index(name = "idx_diffusion_operation", columnList = "operation_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationDiffusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "date_heure", nullable = false)
    private LocalDateTime dateHeure;

    /** Langue de diffusion (optionnelle). */
    @Column(name = "langue_id")
    private Long langueId;

    /** Libellé de la langue figé à l'enregistrement (snapshot). */
    @Column(name = "langue_libelle", length = 60)
    private String langueLibelle;
}

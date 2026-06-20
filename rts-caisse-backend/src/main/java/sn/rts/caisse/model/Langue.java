package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Langue de diffusion à l'antenne (français, wolof, pulaar, mandingue, sérère,
 * diola…). Référentiel géré par l'ADMIN depuis l'application web. Une langue
 * peut être associée à un créneau de diffusion d'une opération (optionnel),
 * pour les produits qui le permettent (cf. {@code CategorieOperation.proposeLangue}).
 */
@Entity
@Table(name = "langues",
        uniqueConstraints = @UniqueConstraint(name = "uk_langue_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Langue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String libelle;

    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("true")
    @Builder.Default
    private boolean actif = true;
}

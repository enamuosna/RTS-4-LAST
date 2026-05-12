package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "banque", indexes = {
        @Index(name = "idx_banque_code", columnList = "code", unique = true),
        @Index(name = "idx_banque_actif", columnList = "actif")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Banque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code interne unique (ex : B01, B02, BHS). */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    /** Libellé / raison sociale (ex : BICIS, SGBS). */
    @Column(nullable = false, length = 200)
    private String libelle;

    /** Pays. Défaut : SÉNÉGAL. */
    @Column(nullable = false, length = 80)
    @Builder.Default
    private String pays = "SÉNÉGAL";

    /** Code établissement BCEAO (ex : SN010). */
    @Column(name = "code_etablissement", length = 20)
    private String codeEtablissement;

    /** Site Internet (optionnel). */
    @Column(name = "site_internet", length = 255)
    private String siteInternet;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.pays == null || this.pays.isBlank()) this.pays = "SÉNÉGAL";
        if (this.actif == null) this.actif = true;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
package sn.rts.caisse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Versement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Accès aux versements bancaires effectués depuis les caisses.
 *
 * <p>Le champ {@code fichierBordereau} étant marqué {@code @Basic(LAZY)},
 * les requêtes ci-dessous ne chargent <b>pas</b> le contenu binaire des
 * bordereaux. Pour récupérer le fichier d'un versement précis, charger
 * l'entité par {@link #findById} puis accéder à {@code getFichierBordereau()}
 * dans une transaction active.</p>
 */
@Repository
public interface VersementRepository extends JpaRepository<Versement, Long> {

    /** Versements d'une caisse, du plus récent au plus ancien. */
    Page<Versement> findByCaisseIdOrderByDateVersementDesc(Long caisseId, Pageable pageable);

    /** Versements d'une caisse sur une plage de dates [debut, fin[. */
    Page<Versement> findByCaisseIdAndDateVersementBetweenOrderByDateVersementDesc(
            Long caisseId, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    /** Versements rattachés à un journal donné (pour l'export Excel). */
    List<Versement> findByJournalIdOrderByDateVersementAsc(Long journalId);

    /** Tous les versements (admin/superviseur), du plus récent au plus ancien. */
    Page<Versement> findAllByOrderByDateVersementDesc(Pageable pageable);

    /** Total des versements d'une caisse sur une période, pour calculs récap. */
    @Query("SELECT COALESCE(SUM(v.montant), 0) FROM Versement v "
            + "WHERE v.caisse.id = :caisseId "
            + "  AND v.dateVersement >= :debut "
            + "  AND v.dateVersement <  :fin")
    BigDecimal totalParCaisseEtPeriode(@Param("caisseId") Long caisseId,
                                       @Param("debut")    LocalDateTime debut,
                                       @Param("fin")      LocalDateTime fin);

    /** Total des versements rattachés à un journal donné. */
    @Query("SELECT COALESCE(SUM(v.montant), 0) FROM Versement v "
            + "WHERE v.journal.id = :journalId")
    BigDecimal totalParJournal(@Param("journalId") Long journalId);
}

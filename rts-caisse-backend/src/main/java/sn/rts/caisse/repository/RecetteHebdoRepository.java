package sn.rts.caisse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.RecetteHebdo;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RecetteHebdoRepository extends JpaRepository<RecetteHebdo, Long> {

    /** Ventilation existante pour une caisse et une période exacte. */
    Optional<RecetteHebdo> findByCaisseIdAndDateDebutAndDateFin(
            Long caisseId, LocalDate dateDebut, LocalDate dateFin);

    /** Historique d'une caisse, de la période la plus récente à la plus ancienne. */
    Page<RecetteHebdo> findByCaisseIdOrderByDateDebutDesc(Long caisseId, Pageable pageable);

    /** Historique global (toutes caisses). */
    Page<RecetteHebdo> findAllByOrderByDateDebutDesc(Pageable pageable);
}

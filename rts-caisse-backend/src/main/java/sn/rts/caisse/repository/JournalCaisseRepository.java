package sn.rts.caisse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.JournalCaisse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalCaisseRepository extends JpaRepository<JournalCaisse, Long> {

    /** Journal d'une caisse pour une date donnée (une seule ouverture possible par jour). */
    Optional<JournalCaisse> findByCaisseIdAndDateJournal(Long caisseId, LocalDate date);

    /** Journal ouvert (non clôturé) pour une caisse. */
    Optional<JournalCaisse> findByCaisseIdAndClotureFalse(Long caisseId);

    List<JournalCaisse> findByDateJournal(LocalDate date);

    List<JournalCaisse> findByCaisseIdOrderByDateJournalDesc(Long caisseId);

    /** Journaux d'une caisse sur une plage [dateDebut, dateFin] inclusives, ordre desc. */
    List<JournalCaisse> findByCaisseIdAndDateJournalBetweenOrderByDateJournalDesc(
            Long caisseId, LocalDate dateDebut, LocalDate dateFin);

    /** Tous les journaux sur une plage [dateDebut, dateFin] inclusives, ordre desc. */
    List<JournalCaisse> findByDateJournalBetweenOrderByDateJournalDesc(
            LocalDate dateDebut, LocalDate dateFin);

    /** Variantes paginees (page admin web Journaux). Le tri est passe via
     *  Pageable.getSort() pour permettre date_journal,desc / asc. */
    Page<JournalCaisse> findByCaisseIdAndDateJournalBetween(
            Long caisseId, LocalDate dateDebut, LocalDate dateFin, Pageable pageable);

    Page<JournalCaisse> findByDateJournalBetween(
            LocalDate dateDebut, LocalDate dateFin, Pageable pageable);
}

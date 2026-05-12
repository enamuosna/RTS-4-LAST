package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Banque;

import java.util.List;
import java.util.Optional;

@Repository
public interface BanqueRepository extends JpaRepository<Banque, Long> {
    Optional<Banque> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    List<Banque> findAllByActifTrueOrderByLibelleAsc();
    List<Banque> findAllByOrderByCodeAsc();
}
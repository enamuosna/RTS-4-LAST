package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Langue;

import java.util.List;

@Repository
public interface LangueRepository extends JpaRepository<Langue, Long> {

    List<Langue> findByActifTrueOrderByLibelleAsc();

    List<Langue> findAllByOrderByLibelleAsc();

    boolean existsByCodeIgnoreCase(String code);
}

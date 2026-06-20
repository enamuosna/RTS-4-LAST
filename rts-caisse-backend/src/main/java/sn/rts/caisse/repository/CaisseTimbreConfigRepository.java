package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.CaisseTimbreConfig;

import java.util.Optional;

@Repository
public interface CaisseTimbreConfigRepository extends JpaRepository<CaisseTimbreConfig, Long> {

    Optional<CaisseTimbreConfig> findByCaisseId(Long caisseId);
}

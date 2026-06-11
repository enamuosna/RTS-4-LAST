package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.TimbreConfig;

@Repository
public interface TimbreConfigRepository extends JpaRepository<TimbreConfig, Long> {
}

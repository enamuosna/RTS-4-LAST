package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.ParametresRecu;

@Repository
public interface ParametresRecuRepository extends JpaRepository<ParametresRecu, Long> {
}

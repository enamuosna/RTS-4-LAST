package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.OperationDiffusion;

import java.util.List;

@Repository
public interface OperationDiffusionRepository extends JpaRepository<OperationDiffusion, Long> {

    List<OperationDiffusion> findByOperationIdOrderByDateHeureAsc(Long operationId);

    void deleteByOperationId(Long operationId);
}

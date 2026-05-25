package sn.rts.caisse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // --- Variantes paginees pour la page admin web ---

    Page<Banque> findAllByActifTrue(Pageable pageable);

    @Query("""
            SELECT b FROM Banque b
            WHERE LOWER(b.code) LIKE LOWER(CONCAT('%', :terme, '%'))
               OR LOWER(b.libelle) LIKE LOWER(CONCAT('%', :terme, '%'))
               OR LOWER(COALESCE(b.codeEtablissement, '')) LIKE LOWER(CONCAT('%', :terme, '%'))
            """)
    Page<Banque> rechercher(@Param("terme") String terme, Pageable pageable);

    @Query("""
            SELECT b FROM Banque b
            WHERE b.actif = true AND (
                LOWER(b.code) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(b.libelle) LIKE LOWER(CONCAT('%', :terme, '%'))
                OR LOWER(COALESCE(b.codeEtablissement, '')) LIKE LOWER(CONCAT('%', :terme, '%'))
            )
            """)
    Page<Banque> rechercherActives(@Param("terme") String terme, Pageable pageable);
}
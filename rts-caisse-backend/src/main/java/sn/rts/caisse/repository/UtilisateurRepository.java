package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Role;
import sn.rts.caisse.model.Utilisateur;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByLogin(String login);

    Optional<Utilisateur> findByMatricule(String matricule);

    boolean existsByLogin(String login);

    boolean existsByMatricule(String matricule);

    /**
     * Comparaisons case-insensitive (Spring Data le génère via LOWER()).
     * Évite les faux négatifs lors de la création d'utilisateur :
     * "Fatou" et "fatou" sont considérés comme le même login.
     */
    boolean existsByLoginIgnoreCase(String login);

    boolean existsByMatriculeIgnoreCase(String matricule);

    /** Pré-check email : utile si une contrainte unique existe en BDD. */
    boolean existsByEmailIgnoreCase(String email);

    /** Pré-check téléphone : utile si une contrainte unique existe en BDD. */
    boolean existsByTelephone(String telephone);

    List<Utilisateur> findByRole(Role role);

    List<Utilisateur> findByActifTrue();
}

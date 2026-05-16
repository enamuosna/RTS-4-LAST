package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.DashboardResponse;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.Role;
import sn.rts.caisse.model.StatutCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrégations pour le tableau de bord Angular Material.
 *
 * <p>Le filtrage par caissier est appliqué automatiquement : un utilisateur
 * de rôle {@link Role#CAISSIER} ne voit que ses propres opérations, tandis
 * que les {@link Role#ADMIN} et {@link Role#SUPERVISEUR} ont la vue globale.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingService {

    private final OperationCaisseRepository operationRepository;
    private final CaisseRepository caisseRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * @param dateDebut début de période (incluse). Si null, prend la valeur
     *                  de {@code dateFin}, ou {@code LocalDate.now()} si les
     *                  deux sont null.
     * @param dateFin   fin de période (incluse). Si null, prend la valeur
     *                  de {@code dateDebut}, ou {@code LocalDate.now()} si
     *                  les deux sont null.
     * @param login     login de l'utilisateur connecté ; utilisé pour
     *                  appliquer le filtre par caissier si le rôle est
     *                  {@link Role#CAISSIER}.
     */
    public DashboardResponse dashboard(LocalDate dateDebut, LocalDate dateFin, String login) {
        return dashboard(dateDebut, dateFin, login, null);
    }

    /**
     * Variante avec filtre optionnel sur une caisse (utilisé par la page
     * Supervision détaillée par caisse). Si {@code caisseId == null},
     * comportement identique à la signature précédente.
     */
    public DashboardResponse dashboard(LocalDate dateDebut, LocalDate dateFin,
                                       String login, Long caisseId) {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutEffectif = dateDebut != null ? dateDebut
                : (dateFin != null ? dateFin : aujourdhui);
        LocalDate finEffective  = dateFin != null ? dateFin
                : (dateDebut != null ? dateDebut : aujourdhui);

        // Si le client a inversé les bornes par erreur, on les remet dans l'ordre.
        if (finEffective.isBefore(debutEffectif)) {
            LocalDate tmp = debutEffectif;
            debutEffectif = finEffective;
            finEffective = tmp;
        }

        LocalDateTime debut = debutEffectif.atStartOfDay();
        LocalDateTime fin   = finEffective.plusDays(1).atStartOfDay();

        // Filtre par caissier si rôle CAISSIER
        Utilisateur utilisateur = utilisateurRepository.findByLogin(login)
                .orElseThrow(() -> new BusinessException(
                        "Utilisateur introuvable : " + login));
        boolean restreintAuCaissier = utilisateur.getRole() == Role.CAISSIER;
        Long caissierId = utilisateur.getId();

        List<OperationCaisse> operations = operationRepository
                .findByDateOperationBetween(debut, fin).stream()
                .filter(o -> !o.isAnnulee())
                .filter(o -> !restreintAuCaissier
                        || (o.getCaissier() != null
                            && caissierId.equals(o.getCaissier().getId())))
                .filter(o -> caisseId == null
                        || (o.getCaisse() != null
                            && caisseId.equals(o.getCaisse().getId())))
                .toList();

        BigDecimal totalEntrees = sommerParType(operations, TypeOperation.ENTREE);
        BigDecimal totalSorties = sommerParType(operations, TypeOperation.SORTIE);
        BigDecimal soldeNet = totalEntrees.subtract(totalSorties);

        // Nombre de caisses ouvertes : pour un caissier, on ne compte que
        // celles dont il est l'agent affecté (cohérent avec la restriction).
        // Pour le mode détaillé d'une caisse, c'est 0 ou 1.
        long caissesOuvertes;
        if (caisseId != null) {
            caissesOuvertes = caisseRepository.findById(caisseId)
                    .filter(c -> c.getStatut() == StatutCaisse.OUVERTE).isPresent() ? 1 : 0;
        } else if (restreintAuCaissier) {
            caissesOuvertes = caisseRepository.findByStatut(StatutCaisse.OUVERTE).stream()
                    .filter(c -> c.getCaissier() != null
                            && caissierId.equals(c.getCaissier().getId()))
                    .count();
        } else {
            caissesOuvertes = caisseRepository.findByStatut(StatutCaisse.OUVERTE).size();
        }

        return new DashboardResponse(
                debutEffectif,
                finEffective,
                totalEntrees,
                totalSorties,
                soldeNet,
                operations.size(),
                caissesOuvertes,
                repartitionParCaisse(operations),
                repartitionParCategorie(operations));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private BigDecimal sommerParType(List<OperationCaisse> operations, TypeOperation type) {
        return operations.stream()
                .filter(o -> o.getTypeOperation() == type)
                .map(OperationCaisse::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<DashboardResponse.LigneCaisse> repartitionParCaisse(List<OperationCaisse> operations) {
        Map<Caisse, List<OperationCaisse>> parCaisse = operations.stream()
                .collect(Collectors.groupingBy(OperationCaisse::getCaisse));

        return parCaisse.entrySet().stream()
                .map(entry -> {
                    Caisse c = entry.getKey();
                    BigDecimal entrees = sommerParType(entry.getValue(), TypeOperation.ENTREE);
                    BigDecimal sorties = sommerParType(entry.getValue(), TypeOperation.SORTIE);
                    return new DashboardResponse.LigneCaisse(
                            c.getId(), c.getCode(), c.getLibelle(),
                            entrees, sorties, entrees.subtract(sorties));
                })
                .sorted(Comparator.comparing(DashboardResponse.LigneCaisse::codeCaisse))
                .toList();
    }

    private List<DashboardResponse.LigneCategorie> repartitionParCategorie(List<OperationCaisse> operations) {
        return operations.stream()
                .collect(Collectors.groupingBy(OperationCaisse::getCategorie))
                .entrySet().stream()
                .map(entry -> {
                    var cat = entry.getKey();
                    BigDecimal total = entry.getValue().stream()
                            .map(OperationCaisse::getMontant)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DashboardResponse.LigneCategorie(
                            cat.getId(), cat.getCode(), cat.getLibelle(),
                            cat.getTypeOperation().name(),
                            total, entry.getValue().size());
                })
                .sorted(Comparator.comparing(DashboardResponse.LigneCategorie::codeCategorie))
                .toList();
    }
}

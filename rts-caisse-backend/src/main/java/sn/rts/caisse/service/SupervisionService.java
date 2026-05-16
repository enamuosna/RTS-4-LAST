package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.SupervisionSnapshotResponse;
import sn.rts.caisse.dto.SupervisionSnapshotResponse.ActiviteRecente;
import sn.rts.caisse.dto.SupervisionSnapshotResponse.EtatCaisse;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.StatutCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Snapshot temps réel pour la page Supervision (Responsable des caisses RTS).
 *
 * <p>Construit en une seule transaction lecture-seule un cliché complet de
 * l'état des caisses pour aujourd'hui : entrées/sorties, soldes, dernière
 * opération par caisse, et les N dernières opérations toutes caisses
 * confondues pour le flux d'activité.</p>
 *
 * <p>La page Angular polle cet endpoint toutes les 10 secondes.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupervisionService {

    /** Nombre d'opérations dans le flux d'activité récente. */
    private static final int LIMITE_ACTIVITE = 20;

    private final CaisseRepository caisseRepository;
    private final OperationCaisseRepository operationRepository;

    public SupervisionSnapshotResponse snapshot() {
        return snapshot(null, null);
    }

    /**
     * Variante avec plage de dates optionnelle. Sans dates -> journee courante
     * (comportement legacy, mode "temps reel"). Avec dates -> vue historique
     * sur [dateDebut, dateFin] inclusives.
     */
    public SupervisionSnapshotResponse snapshot(LocalDate dateDebut, LocalDate dateFin) {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate d1 = dateDebut != null ? dateDebut
                : (dateFin != null ? dateFin : aujourdhui);
        LocalDate d2 = dateFin   != null ? dateFin
                : (dateDebut != null ? dateDebut : aujourdhui);
        if (d2.isBefore(d1)) { LocalDate tmp = d1; d1 = d2; d2 = tmp; }
        LocalDateTime debutJour = d1.atStartOfDay();
        LocalDateTime finJour   = d2.plusDays(1).atStartOfDay();

        // Toutes les caisses (pour les compteurs total/ouvert)
        List<Caisse> toutes = caisseRepository.findAll();
        long caissesOuvertes = toutes.stream()
                .filter(c -> c.getStatut() == StatutCaisse.OUVERTE)
                .count();

        // Toutes les opérations du jour, non annulées
        List<OperationCaisse> opsJour = operationRepository
                .findByDateOperationBetween(debutJour, finJour).stream()
                .filter(o -> !o.isAnnulee())
                .toList();

        // Group par caisse pour les agrégats par caisse
        Map<Long, List<OperationCaisse>> parCaisseId = opsJour.stream()
                .collect(Collectors.groupingBy(o -> o.getCaisse().getId()));

        // Totaux globaux
        BigDecimal totalEntrees = sommer(opsJour, TypeOperation.ENTREE);
        BigDecimal totalSorties = sommer(opsJour, TypeOperation.SORTIE);

        // Construire la liste des EtatCaisse (toutes les caisses, dans l'ordre du code)
        List<EtatCaisse> etatCaisses = toutes.stream()
                .sorted(Comparator.comparing(Caisse::getCode))
                .map(c -> {
                    List<OperationCaisse> opsCaisse = parCaisseId
                            .getOrDefault(c.getId(), List.of());
                    BigDecimal entrees = sommer(opsCaisse, TypeOperation.ENTREE);
                    BigDecimal sorties = sommer(opsCaisse, TypeOperation.SORTIE);
                    LocalDateTime derniere = opsCaisse.stream()
                            .map(OperationCaisse::getDateOperation)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return new EtatCaisse(
                            c.getId(),
                            c.getCode(),
                            c.getLibelle(),
                            c.getStatut(),
                            c.getCaissier() != null ? c.getCaissier().getNomComplet() : null,
                            c.getSoldeCourant(),
                            opsCaisse.size(),
                            entrees,
                            sorties,
                            derniere);
                })
                .toList();

        // Activité récente : les N dernières opérations toutes caisses confondues
        List<ActiviteRecente> activite = opsJour.stream()
                .sorted(Comparator.comparing(OperationCaisse::getDateOperation,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LIMITE_ACTIVITE)
                .map(o -> new ActiviteRecente(
                        o.getId(),
                        o.getNumeroRecu(),
                        o.getDateOperation(),
                        o.getCaisse().getCode(),
                        o.getCaissier().getNomComplet(),
                        o.getTypeOperation(),
                        o.getMontantTtc() != null ? o.getMontantTtc() : o.getMontant(),
                        o.getCategorie().getLibelle(),
                        o.getClient() != null ? o.getClient().getRaisonSociale() : null,
                        o.isAnnulee()))
                .toList();

        return new SupervisionSnapshotResponse(
                LocalDateTime.now(),
                toutes.size(),
                caissesOuvertes,
                totalEntrees,
                totalSorties,
                totalEntrees.subtract(totalSorties),
                etatCaisses,
                activite);
    }

    private static BigDecimal sommer(List<OperationCaisse> ops, TypeOperation type) {
        return ops.stream()
                .filter(o -> o.getTypeOperation() == type)
                .map(o -> o.getMontantTtc() != null ? o.getMontantTtc() : o.getMontant())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

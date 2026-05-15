package sn.rts.caisse.dto;

import sn.rts.caisse.model.StatutCaisse;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Snapshot temps réel pour la page Supervision (responsable des caisses).
 *
 * <p>Renvoyé par {@code GET /api/supervision/snapshot}. La page Angular
 * appelle cet endpoint toutes les 10 secondes pour rafraîchir l'affichage.</p>
 *
 * @param horodatage    moment exact de la capture (côté serveur)
 * @param totalCaisses  nombre total de caisses configurées (toutes statuts)
 * @param caissesOuvertes nombre de caisses ouvertes
 * @param totalEntreesJour total des encaissements du jour (toutes caisses)
 * @param totalSortiesJour total des décaissements du jour (toutes caisses)
 * @param soldeNetJour    différence entrées/sorties du jour
 * @param caisses        état détaillé de chaque caisse (1 ligne par caisse)
 * @param activiteRecente N dernières opérations toutes caisses confondues
 *                        (pour le flux d'activité)
 */
public record SupervisionSnapshotResponse(
        LocalDateTime horodatage,
        long totalCaisses,
        long caissesOuvertes,
        BigDecimal totalEntreesJour,
        BigDecimal totalSortiesJour,
        BigDecimal soldeNetJour,
        List<EtatCaisse> caisses,
        List<ActiviteRecente> activiteRecente
) {

    /**
     * État courant d'une caisse pour la grille de supervision.
     */
    public record EtatCaisse(
            Long id,
            String code,
            String libelle,
            StatutCaisse statut,
            String caissierNomComplet,
            BigDecimal soldeCourant,
            long nombreOperationsJour,
            BigDecimal totalEntreesJour,
            BigDecimal totalSortiesJour,
            LocalDateTime derniereOperationLe
    ) {}

    /**
     * Une opération récente pour le flux d'activité.
     */
    public record ActiviteRecente(
            Long operationId,
            String numeroRecu,
            LocalDateTime dateOperation,
            String caisseCode,
            String caissierNom,
            TypeOperation typeOperation,
            BigDecimal montantTtc,
            String categorieLibelle,
            String clientRaisonSociale,
            boolean annulee
    ) {}
}

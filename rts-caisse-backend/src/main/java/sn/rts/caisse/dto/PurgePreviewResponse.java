package sn.rts.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Résumé de ce qui sera supprimé si la purge est validée.
 *
 * <p>Renvoyé par {@code POST /api/operations/purger/preview} pour permettre
 * à l'ADMIN de visualiser l'impact avant confirmation.</p>
 *
 * @param total          nombre total d'opérations qui seront supprimées
 * @param nbAnnulees     parmi ces opérations, combien sont déjà annulées (sans impact solde)
 * @param nbActives      parmi ces opérations, combien sont actives (contre-pass solde auto)
 * @param nbDansCloture  parmi les actives, combien sont dans un journal clôturé (delete sans contre-pass)
 * @param sommeEntrees   somme des montants TTC des ENTREE actives (impact solde si purgées)
 * @param sommeSorties   somme des montants TTC des SORTIE actives
 * @param plusAncienne   date de la plus ancienne opération concernée
 * @param plusRecente    date de la plus récente
 */
public record PurgePreviewResponse(
        long total,
        long nbAnnulees,
        long nbActives,
        long nbDansCloture,
        BigDecimal sommeEntrees,
        BigDecimal sommeSorties,
        LocalDateTime plusAncienne,
        LocalDateTime plusRecente
) {}

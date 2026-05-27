package sn.rts.caisse.dto;

import java.util.List;

/**
 * Résultat d'une exécution de purge (single ou bulk).
 *
 * @param nbSupprimees      nombre d'opérations effectivement supprimées
 * @param nbContrepassees   nombre de soldes ajustés (contre-passation auto)
 * @param nbEchecs          nombre d'opérations qui n'ont pas pu être supprimées
 * @param erreurs           messages d'erreur (1 par échec, max 10)
 */
public record PurgeResult(
        int nbSupprimees,
        int nbContrepassees,
        int nbEchecs,
        List<String> erreurs
) {}

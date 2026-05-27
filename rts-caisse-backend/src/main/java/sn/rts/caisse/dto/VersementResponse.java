package sn.rts.caisse.dto;

import sn.rts.caisse.model.Versement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload de réponse pour un versement bancaire.
 *
 * <p>Le contenu binaire du bordereau ({@code fichierBordereau} de l'entité)
 * n'est <b>pas</b> exposé dans ce DTO : il est récupéré via l'endpoint
 * dédié {@code GET /api/versements/{id}/fichier}. Seuls le nom du fichier,
 * son type MIME et sa taille sont communiqués pour permettre l'affichage
 * d'une icône et d'un lien de téléchargement.</p>
 */
public record VersementResponse(
        Long          id,
        Long          caisseId,
        String        caisseLibelle,
        Long          journalId,
        Long          banqueId,
        String        banqueCode,
        String        banqueLibelle,
        BigDecimal    montant,
        String        numeroBordereau,
        LocalDateTime dateVersement,
        String        nomFichier,
        String        typeMime,
        Long          tailleFichier,
        Long          createdById,
        String        createdByNom,
        LocalDateTime createdAt,
        String        notes
) {
    public static VersementResponse from(Versement v) {
        return new VersementResponse(
                v.getId(),
                v.getCaisse().getId(),
                v.getCaisse().getLibelle(),
                v.getJournal() != null ? v.getJournal().getId() : null,
                v.getBanque().getId(),
                v.getBanque().getCode(),
                v.getBanque().getLibelle(),
                v.getMontant(),
                v.getNumeroBordereau(),
                v.getDateVersement(),
                v.getNomFichier(),
                v.getTypeMime(),
                v.getTailleFichier(),
                v.getCreatedBy().getId(),
                v.getCreatedBy().getNomComplet(),
                v.getCreatedAt(),
                v.getNotes()
        );
    }
}

package sn.rts.caisse.dto;

import sn.rts.caisse.model.Banque;
import sn.rts.caisse.model.Client;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Réponse renvoyée par l'API après enregistrement / consultation
 * d'une opération de caisse.
 *
 * <p>L'ordre des champs ci-dessous est l'ordre de référence : la méthode
 * {@link #from(OperationCaisse)} appelle le constructeur positionnel et
 * doit respecter strictement ce même ordre.</p>
 *
 * <p>Évolutions :</p>
 * <ul>
 *   <li><b>v2</b> : ajout de {@code clientTelephone} et {@code clientAdresse}
 *       pour l'impression du reçu enrichi.</li>
 *   <li><b>v3 (mai 2026)</b> : ajout de {@code clientIdentifiantFiscal} et
 *       du bloc {@code banqueId / banqueCode / banqueLibelle /
 *       banqueCodeEtablissement / banqueSiteInternet} pour l'affichage
 *       sur le reçu lors des règlements par chèque ou virement.</li>
 * </ul>
 */
public record OperationCaisseResponse(

        // ---------- Identification de l'opération ----------
        Long          id,
        String        numeroRecu,
        TypeOperation typeOperation,
        BigDecimal    montant,
        BigDecimal    timbre,
        BigDecimal    montantTtc,
        String        motif,
        ModePaiement  modePaiement,
        String        reference,
        LocalDateTime dateOperation,

        // ---------- Caisse ----------
        Long   caisseId,
        String caisseLibelle,

        // ---------- Caissier ----------
        Long   caissierId,
        String caissierNomComplet,

        // ---------- Catégorie ----------
        Long   categorieId,
        String categorieLibelle,

        // ---------- Client (optionnel) ----------
        Long   clientId,
        String clientRaisonSociale,
        String clientIdentifiantFiscal,
        String clientTelephone,
        String clientAdresse,

        // ---------- Banque (CHEQUE / VIREMENT) ----------
        Long   banqueId,
        String banqueCode,
        String banqueLibelle,
        String banqueCodeEtablissement,
        String banqueSiteInternet,

        // ---------- Annulation ----------
        boolean annulee,
        String  motifAnnulation
) {

    /**
     * Conversion entité → DTO. À appeler dans une transaction active
     * (lazy loading des relations client et banque).
     */
    public static OperationCaisseResponse from(OperationCaisse o) {
        Client c = o.getClient();
        Banque b = o.getBanque();

        return new OperationCaisseResponse(
                // Identification
                o.getId(),
                o.getNumeroRecu(),
                o.getTypeOperation(),
                o.getMontant(),
                o.getTimbre(),
                o.getMontantTtc(),
                o.getMotif(),
                o.getModePaiement(),
                o.getReference(),
                o.getDateOperation(),

                // Caisse
                o.getCaisse().getId(),
                o.getCaisse().getLibelle(),

                // Caissier
                o.getCaissier().getId(),
                o.getCaissier().getNomComplet(),

                // Catégorie
                o.getCategorie().getId(),
                o.getCategorie().getLibelle(),

                // Client
                c != null ? c.getId()                : null,
                c != null ? c.getRaisonSociale()     : null,
                c != null ? c.getIdentifiantFiscal() : null,
                c != null ? c.getTelephone()         : null,
                c != null ? c.getAdresse()           : null,

                // Banque
                b != null ? b.getId()                : null,
                b != null ? b.getCode()              : null,
                b != null ? b.getLibelle()           : null,
                b != null ? b.getCodeEtablissement() : null,
                b != null ? b.getSiteInternet()      : null,

                // Annulation
                o.isAnnulee(),
                o.getMotifAnnulation()
        );
    }
}
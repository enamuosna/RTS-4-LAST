package sn.rts.caisse.guichet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Miroirs des DTOs Spring Boot, utilisés par Jackson pour (dé)sérialiser
 * les réponses de l'API. On utilise des POJOs plutôt que des records car
 * les bindings JavaFX sont plus simples avec des mutables.
 */
public final class Dto {

    private Dto() {}

    // ====================================================
    //  AUTH
    // ====================================================

    public static class LoginRequest {
        public String login;
        public String motDePasse;
        public LoginRequest() {}
        public LoginRequest(String login, String mdp) {
            this.login = login; this.motDePasse = mdp;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthResponse {
        public String token;
        public String type;
        public long expiresInMs;
        public Long utilisateurId;
        public String matricule;
        public String login;
        public String nomComplet;
        public Role role;
    }

    // ====================================================
    //  UTILISATEUR / CAISSE / JOURNAL / OPERATION
    // ====================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CaisseDTO {
        public Long id;
        public String code;
        public String libelle;
        public String emplacement;
        public StatutCaisse statut;
        public BigDecimal soldeCourant;
        public Long caissierId;
        public String caissierNomComplet;
        /** Agent de recette rattaché à cette caisse (peut modifier les opérations). */
        public Long agentRecetteId;
        public String agentRecetteNomComplet;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategorieDTO {
        public Long id;
        public String code;
        public String libelle;
        public TypeOperation typeOperation;
        public boolean actif;

        @Override public String toString() { return libelle; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientDTO {
        public Long id;
        public String raisonSociale;
        public String identifiantFiscal;
        public String telephone;
        public String email;
        public String adresse;
        public boolean actif;

        @Override public String toString() { return raisonSociale; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OuvertureCaisseRequest {
        public BigDecimal fondOuverture;
        public OuvertureCaisseRequest() {}
        public OuvertureCaisseRequest(BigDecimal fond) { this.fondOuverture = fond; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClotureCaisseRequest {
        public BigDecimal soldeReel;
        public String commentaire;
        public ClotureCaisseRequest() {}
        public ClotureCaisseRequest(BigDecimal reel, String commentaire) {
            this.soldeReel = reel; this.commentaire = commentaire;
        }
    }

    // -----------------------------------------------------------
    //  NOUVEAU : payload de création de client depuis le guichet
    //  Sérialisé en JSON par Jackson :
    //    { "raisonSociale": "...", "identifiantFiscal": "...",
    //      "telephone": "...", "email": "...", "adresse": "..." }
    //  Les champs null sont omis si Jackson est configuré avec
    //  Include.NON_NULL (cas standard du projet).
    // -----------------------------------------------------------
    public static class ClientCreateRequest {
        public String raisonSociale;
        public String identifiantFiscal;   // NINEA / RCCM / CNI
        public String telephone;
        public String email;
        public String adresse;

        public ClientCreateRequest() {}

        public ClientCreateRequest(String raisonSociale,
                                   String identifiantFiscal,
                                   String telephone,
                                   String email,
                                   String adresse) {
            this.raisonSociale     = raisonSociale;
            this.identifiantFiscal = identifiantFiscal;
            this.telephone         = telephone;
            this.email             = email;
            this.adresse           = adresse;
        }

        /**
         * Validation locale avant envoi au backend : seul
         * "raisonSociale" est strictement obligatoire. Les autres
         * champs ne sont pas vérifiés (le backend a ses propres
         * contraintes Bean Validation @Email, @Size, etc.).
         */
        public String validerLocalement() {
            if (raisonSociale == null || raisonSociale.isBlank()) {
                return "La raison sociale est obligatoire.";
            }
            return null; // OK
        }

        /**
         * Normalise les valeurs : trim partout, null pour les
         * champs vides (pour éviter d'envoyer "" au backend).
         */
        public void normaliser() {
            raisonSociale     = trimOuNull(raisonSociale);
            identifiantFiscal = trimOuNull(identifiantFiscal);
            telephone         = trimOuNull(telephone);
            email             = trimOuNull(email);
            adresse           = trimOuNull(adresse);
        }

        private static String trimOuNull(String s) {
            if (s == null) return null;
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JournalCaisseResponse {
        public Long id;
        public String dateJournal;
        public Long caisseId;
        public String caisseLibelle;
        public Long caissierId;
        public String caissierNomComplet;
        public BigDecimal fondOuverture;
        public BigDecimal totalEntrees;
        public BigDecimal totalSorties;
        public BigDecimal soldeTheorique;
        public BigDecimal soldeReel;
        public BigDecimal ecart;
        public String commentaire;
        public LocalDateTime ouvertLe;
        public LocalDateTime clotureLe;
        public boolean cloture;
        public Long valideeParId;
        public String valideeParNom;
    }

    // ====================================================
    //  WHATSAPP CLOUD API
    // ====================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnvoiWhatsAppRequest {
        public String telephone;
        public EnvoiWhatsAppRequest() {}
        public EnvoiWhatsAppRequest(String telephone) { this.telephone = telephone; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnvoiWhatsAppResponse {
        public boolean envoye;
        public String messageId;
        public String destinataire;
        public String messageErreur;
    }
    /**
     * Référentiel d'une banque (réception depuis le backend, lecture seule).
     */
    // ====================================================
    //  PARAMÈTRES DU REÇU
    // ====================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SectionRecu {
        public String id;
        public boolean visible;
        public SectionRecu() {}
    }

    /** Miroir de {@code ParametresRecuDto} côté backend. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParametresRecuDto {
        // En-tête
        public String logoTexte;
        public String raisonSociale;
        public String sousTitreEntete;
        public String ligneLegale;
        public String capital;
        public String adresse;
        public String telephone;
        public String boitePostale;
        public String ninea;

        // Footer
        public String footerLigne1;
        public String footerLigne2;
        public String villeSignature;

        // Couleurs (hex #RRGGBB)
        public String couleurPrimaire;
        public String couleurAccent;
        public String couleurTexte;
        public String couleurTexteSecondaire;
        public String couleurSuccess;
        public String couleurDanger;
        public String couleurFondMontant;

        // Tailles (pt)
        public Integer tailleTitre;
        public Integer tailleEntete;
        public Integer tailleCorps;
        public Integer tailleMontant;
        public Integer tailleFooter;

        // Layout
        public java.util.List<SectionRecu> sections;

        public boolean logoPresent;

        public ParametresRecuDto() {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BanqueDTO {
        public Long id;
        public String code;
        public String libelle;
        public String pays;
        public String codeEtablissement;
        public String siteInternet;
        public boolean actif;

        @Override
        public String toString() {
            // Affichage dans le ComboBox : "B01 - BICIS"
            return (code == null ? "" : code) + " - " + (libelle == null ? "" : libelle);
        }
    }

    public static class OperationCaisseRequest {

        public Long          caisseId;
        public Long          categorieId;
        public Long          clientId;
        public TypeOperation typeOperation;
        public BigDecimal    montant;
        /** Timbre fiscal (taxe optionnelle, FCFA). Peut être null = traité comme 0. */
        public BigDecimal    timbre;
        public ModePaiement  modePaiement;

        /** Conservé pour compat ; envoyé null depuis le formulaire v5. */
        public String motif;

        /** N° chèque, ID transaction Wave/OM, bordereau, etc. */
        public String reference;

        /** Banque émettrice — obligatoire si modePaiement = CHEQUE ou VIREMENT. */
        public Long banqueId;

        /**
         * Date+heure prevue de diffusion du produit a l'antenne (spot pub,
         * sponsoring, message). Optionnel : null si pas de diffusion (ex.
         * vente d'archives, prestation). Affiche sur le recu sous "Reference".
         */
        public LocalDateTime dateDiffusion;

        public OperationCaisseRequest() {}
    }


    // ================================================================
    //  OperationCaisseResponse — réponse renvoyée par le backend
    // ================================================================
    //
    //  Désérialisé depuis POST /api/operations ou GET /api/operations/...
    //  Les 25 champs correspondent au record backend.
    //
    //  Utilisé par :
    //   - NouvelleOperationController : callback onSuccess + dialogue
    //     post-enregistrement
    //   - PrintRecu : impression du reçu A6
    //   - RecuExporter : envoi WhatsApp
    //   - CaissierController : rafraîchissement du tableau du jour
    // ================================================================
    public static class OperationCaisseResponse {

        // ----- Identification de l'opération -----
        public Long          id;
        public String        numeroRecu;
        public TypeOperation typeOperation;
        public BigDecimal    montant;
        public BigDecimal    timbre;
        public BigDecimal    montantTtc;
        public String        motif;
        public ModePaiement  modePaiement;
        public String        reference;
        public LocalDateTime dateOperation;
        /** Date+heure de diffusion du produit a l'antenne (optionnel). */
        public LocalDateTime dateDiffusion;

        // ----- Caisse -----
        public Long   caisseId;
        public String caisseLibelle;

        // ----- Caissier (agent connecté qui a saisi l'opération) -----
        public Long   caissierId;
        public String caissierNomComplet;

        // ----- Catégorie comptable -----
        public Long   categorieId;
        public String categorieLibelle;

        // ----- Client (optionnel) -----
        public Long   clientId;
        public String clientRaisonSociale;
        public String clientIdentifiantFiscal;   // NINEA / RCCM / CNI
        public String clientTelephone;
        public String clientAdresse;

        // ----- Banque (renseigné si CHEQUE ou VIREMENT) -----
        public Long   banqueId;
        public String banqueCode;
        public String banqueLibelle;
        public String banqueCodeEtablissement;

        // ----- Statut d'annulation -----
        public boolean annulee;
        public String  motifAnnulation;
        public String banqueSiteInternet;

        public OperationCaisseResponse() {}
    }
}

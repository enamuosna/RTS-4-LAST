package sn.rts.caisse.guichet.api;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.model.Dto.AuthResponse;
import sn.rts.caisse.guichet.model.Dto.ClientAuditEventRequest;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Config;
import java.net.InetAddress;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;
import sn.rts.caisse.guichet.model.Dto.CategorieDTO;
import sn.rts.caisse.guichet.model.Dto.ClientCreateRequest;
import sn.rts.caisse.guichet.model.Dto.ClientDTO;
import sn.rts.caisse.guichet.model.Dto.ClotureCaisseRequest;
import sn.rts.caisse.guichet.model.Dto.EnvoiWhatsAppRequest;
import sn.rts.caisse.guichet.model.Dto.EnvoiWhatsAppResponse;
import sn.rts.caisse.guichet.model.Dto.JournalCaisseResponse;
import sn.rts.caisse.guichet.model.Dto.LoginRequest;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseRequest;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseResponse;
import sn.rts.caisse.guichet.model.Dto.OuvertureCaisseRequest;
import sn.rts.caisse.guichet.model.TypeOperation;

import java.math.BigDecimal;
import java.util.List;
import sn.rts.caisse.guichet.model.Dto.BanqueDTO;

/**
 * Façade métier simple qui expose les endpoints REST utiles au guichet.
 *
 * <p>Les classes UI ne doivent JAMAIS appeler directement {@link ApiClient} :
 * elles passent toujours par {@link #getInstance()} et appellent une méthode
 * nommée par usage métier. Cela permet :
 * <ul>
 *   <li>de centraliser la construction des URLs et l'encodage des paramètres ;</li>
 *   <li>de typer fortement les requêtes/réponses (pas de {@code Map<String,Object>}) ;</li>
 *   <li>de mocker facilement la couche serveur dans les tests UI.</li>
 * </ul>
 *
 * <p>Toutes les méthodes sont <strong>bloquantes</strong> : elles doivent être
 * appelées depuis un thread d'I/O via {@code AsyncRunner.run(...)}, jamais
 * depuis le thread FX.
 */
public class CaisseApi {

    private static final Logger log = LoggerFactory.getLogger(CaisseApi.class);

    private static final CaisseApi INSTANCE = new CaisseApi();

    public static CaisseApi getInstance() {
        return INSTANCE;
    }

    private final ApiClient client = ApiClient.getInstance();

    /** Nom du poste (cache statique, calculé une fois). */
    private static final String HOSTNAME = resolveHostname();

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String env = System.getenv("COMPUTERNAME");
            return env != null ? env : "unknown";
        }
    }

    // ====================================================================
    //  AUTH
    // ====================================================================

    public AuthResponse login(String login, String motDePasse) {
        LoginRequest req = new LoginRequest(login, motDePasse);
        return client.post("/auth/login", req, AuthResponse.class);
    }

    // ====================================================================
    //  CAISSES
    // ====================================================================

    public List<CaisseDTO> listerCaisses() {
        return client.get("/caisses", new TypeReference<List<CaisseDTO>>() {});
    }

    public CaisseDTO obtenirCaisse(Long id) {
        return client.get("/caisses/" + id, CaisseDTO.class);
    }

    // ====================================================================
    //  CATEGORIES
    // ====================================================================

    public List<CategorieDTO> listerCategories(TypeOperation type) {
        String path = "/categories" + (type != null ? "?type=" + type.name() : "");
        return client.get(path, new TypeReference<List<CategorieDTO>>() {});
    }

    // ====================================================================
    //  CLIENTS
    // ====================================================================

    /**
     * Recherche les clients par terme libre (raison sociale, NINEA, e-mail).
     * Si {@code terme} est null ou vide, retourne la liste complète.
     */
    public List<ClientDTO> rechercherClients(String terme) {
        String path = "/clients"
                + (terme != null && !terme.isBlank() ? "?q=" + ApiClient.encode(terme) : "");
        return client.get(path, new TypeReference<List<ClientDTO>>() {});
    }

    public List<BanqueDTO> listerBanques() {
        return client.get("/banques?actives=true",
                new TypeReference<List<BanqueDTO>>() {});
    }

    /**
     * Récupère un client par son identifiant.
     */
    public ClientDTO obtenirClient(Long id) {
        return client.get("/clients/" + id, ClientDTO.class);
    }

    /**
     * Crée un nouveau client en base via {@code POST /api/clients}.
     *
     * <p>Cas d'usage principal : depuis le modal « Nouvelle opération »,
     * lorsque le caissier saisit directement les coordonnées d'un client
     * absent du référentiel (mode « + Nouveau » de la mini-bascule).
     *
     * <p>La validation de Bean Validation est effectuée côté serveur
     * (raison sociale non vide, e-mail valide, tailles maximales). Une
     * normalisation locale ({@link ClientCreateRequest#normaliser()})
     * est appliquée avant l'envoi pour éviter d'envoyer des chaînes vides
     * que le backend interpréterait comme des valeurs.
     *
     * @param requete payload de création (raison sociale obligatoire)
     * @return le client persisté, avec son {@code id} généré par la base
     * @throws ApiException si le serveur refuse (400, 409 doublon, 401…)
     *                      ou si la connexion réseau échoue
     */
    public ClientDTO creerClient(ClientCreateRequest requete) {
        if (requete == null) {
            throw new IllegalArgumentException(
                    "ClientCreateRequest ne peut pas être null.");
        }
        // Trim + vides → null pour ne pas envoyer "" au backend
        requete.normaliser();
        return client.post("/clients", requete, ClientDTO.class);
    }

    /**
     * Met à jour un client existant. Le payload est un {@link ClientDTO}
     * complet (le serveur ignore l'id du body et utilise celui de l'URL).
     */
    public ClientDTO modifierClient(Long id, ClientDTO client) {
        return this.client.put("/clients/" + id, client, ClientDTO.class);
    }

    // ====================================================================
    //  JOURNAL DE CAISSE (ouverture / clôture)
    // ====================================================================

    public JournalCaisseResponse ouvrirCaisse(Long caisseId, BigDecimal fondOuverture) {
        OuvertureCaisseRequest req = new OuvertureCaisseRequest(fondOuverture);
        return client.post("/journaux/caisse/" + caisseId + "/ouvrir", req,
                JournalCaisseResponse.class);
    }

    public JournalCaisseResponse cloturerCaisse(Long caisseId,
                                                BigDecimal soldeReel,
                                                String commentaire) {
        ClotureCaisseRequest req = new ClotureCaisseRequest(soldeReel, commentaire);
        return client.post("/journaux/caisse/" + caisseId + "/cloturer", req,
                JournalCaisseResponse.class);
    }

    // ====================================================================
    //  OPERATIONS DE CAISSE
    // ====================================================================

    public OperationCaisseResponse enregistrerOperation(OperationCaisseRequest req) {
        return client.post("/operations", req, OperationCaisseResponse.class);
    }

    /**
     * Modifie une opération existante (correction de saisie).
     * Le solde de la caisse est recalculé automatiquement côté backend.
     */
    public OperationCaisseResponse modifierOperation(Long id, OperationCaisseRequest req) {
        return client.put("/operations/" + id, req, OperationCaisseResponse.class);
    }

    public List<OperationCaisseResponse> operationsDuJour(Long caisseId) {
        return client.get("/operations/caisse/" + caisseId + "/jour",
                new TypeReference<List<OperationCaisseResponse>>() {});
    }

    /**
     * Opérations de la <b>session de caisse en cours</b> (journal pas encore
     * clôturé). Utilisé par le guichet : la liste devient vide après clôture.
     */
    public List<OperationCaisseResponse> operationsSessionCourante(Long caisseId) {
        return client.get("/operations/caisse/" + caisseId + "/session",
                new TypeReference<List<OperationCaisseResponse>>() {});
    }

    public OperationCaisseResponse annulerOperation(Long operationId, String motif) {
        return client.patch("/operations/" + operationId + "/annuler?motif="
                + ApiClient.encode(motif), null, OperationCaisseResponse.class);
    }

    /** Réactive une opération annulée par erreur (annule la contre-passation). */
    public OperationCaisseResponse reactiverOperation(Long operationId) {
        return client.patch("/operations/" + operationId + "/reactiver",
                null, OperationCaisseResponse.class);
    }

    /**
     * Attache un justificatif (PDF/JPG/PNG, max 5 Mo) a une operation.
     * La categorie de l'operation doit avoir accepteJustificatif=true,
     * sinon le backend rejette avec une BusinessException.
     */
    public OperationCaisseResponse uploaderJustificatif(
            Long operationId, String nomFichier, String typeMime, byte[] fichier) {
        return client.postMultipart(
                "/operations/" + operationId + "/justificatif",
                java.util.Map.of(),
                "fichier", nomFichier, typeMime, fichier,
                OperationCaisseResponse.class);
    }

    // ====================================================================
    //  VERSEMENTS BANCAIRES
    // ====================================================================

    /**
     * Enregistre un versement bancaire avec upload du bordereau (multipart).
     * Le bordereau doit etre PDF / JPG / PNG, max 5 Mo.
     *
     * @param caisseId        caisse d'origine (obligatoire)
     * @param banqueId        banque destinataire (obligatoire)
     * @param journalId       journal a rattacher (optionnel, peut etre null)
     * @param montant         montant verse en FCFA
     * @param numeroBordereau identifiant du bordereau bancaire
     * @param dateVersement   ISO 8601 (peut etre null, backend met now())
     * @param notes           commentaire libre (optionnel)
     * @param nomFichier      nom original du fichier (avec extension)
     * @param typeMime        application/pdf, image/jpeg ou image/png
     * @param fichier         contenu binaire du bordereau
     */
    public sn.rts.caisse.guichet.model.Dto.VersementResponse enregistrerVersement(
            Long caisseId, Long banqueId, Long journalId,
            java.math.BigDecimal montant, String numeroBordereau,
            String dateVersement, String notes,
            String nomFichier, String typeMime, byte[] fichier) {
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("caisseId",        String.valueOf(caisseId));
        params.put("banqueId",        String.valueOf(banqueId));
        params.put("montant",         montant.toPlainString());
        params.put("numeroBordereau", numeroBordereau);
        if (journalId != null)       params.put("journalId",     String.valueOf(journalId));
        if (dateVersement != null)   params.put("dateVersement", dateVersement);
        if (notes != null && !notes.isBlank()) params.put("notes", notes);
        return client.postMultipart(
                "/versements", params,
                "fichier", nomFichier, typeMime, fichier,
                sn.rts.caisse.guichet.model.Dto.VersementResponse.class);
    }

    // ====================================================================
    //  WHATSAPP
    // ====================================================================

    /**
     * Envoie le reçu PDF d'une opération par WhatsApp via le backend RTS.
     *
     * <p>Le PDF est généré côté serveur et acheminé via l'API WhatsApp
     * Business Cloud de Meta. Aucune ouverture de WhatsApp Web/Desktop
     * côté caissier.
     *
     * @param operationId identifiant de l'opération à envoyer
     * @param telephone   numéro WhatsApp brut du destinataire
     *                    (sera normalisé par le backend)
     */
    public EnvoiWhatsAppResponse envoyerOperationWhatsApp(Long operationId, String telephone) {
        EnvoiWhatsAppRequest body = new EnvoiWhatsAppRequest(telephone);
        return client.post(
                "/operations/" + operationId + "/whatsapp",
                body,
                EnvoiWhatsAppResponse.class);
    }

    // ====================================================================
    //  PARAMÈTRES DU REÇU (personnalisation admin)
    // ====================================================================

    public sn.rts.caisse.guichet.model.Dto.ParametresRecuDto obtenirParametresRecu() {
        return client.get("/parametres/recu",
                sn.rts.caisse.guichet.model.Dto.ParametresRecuDto.class);
    }

    /** Configuration du timbre (modèle global par défaut). */
    public sn.rts.caisse.guichet.model.Dto.TimbreConfigDto obtenirTimbreConfig() {
        return client.get("/parametres/timbre",
                sn.rts.caisse.guichet.model.Dto.TimbreConfigDto.class);
    }

    /** Configuration du timbre PROPRE à une caisse (inclut le mode AUTO/MANUEL). */
    public sn.rts.caisse.guichet.model.Dto.TimbreConfigDto obtenirTimbreConfig(Long caisseId) {
        if (caisseId == null) {
            return obtenirTimbreConfig();
        }
        return client.get("/parametres/timbre?caisseId=" + caisseId,
                sn.rts.caisse.guichet.model.Dto.TimbreConfigDto.class);
    }

    /** Renvoie l'image du logo en octets, ou null si aucun logo n'a été déposé. */
    public byte[] obtenirLogoRecu() {
        try {
            return client.getBytes("/parametres/recu/logo");
        } catch (ApiException e) {
            // 404 si pas de logo
            return null;
        }
    }

    // ====================================================
    //  AUDIT (événements émis par le client lourd)
    // ====================================================

    /**
     * Remonte un événement émis par le poste desktop au journal d'audit
     * central, en <b>fire-and-forget</b> (jamais bloquant, jamais d'exception
     * propagée à l'appelant) :
     * <ul>
     *   <li>l'appel HTTP est exécuté sur le pool {@link AsyncRunner}
     *       pour ne pas figer le thread FX,</li>
     *   <li>toute erreur réseau est avalée (loggée localement uniquement) :
     *       le serveur peut être hors ligne (cas typique d'un
     *       {@code ECHEC_CONNEXION_SERVEUR}…) sans casser l'UI.</li>
     * </ul>
     *
     * @param action  nom de l'action (constantes
     *                {@link sn.rts.caisse.guichet.model.Dto.AuditActions})
     * @param success vrai si l'événement représente un succès
     * @param details texte libre (mode, chemin du fichier, motif…)
     */
    public void signalerEvenementAudit(String action, boolean success, String details) {
        signalerEvenementAudit(action, success, null, details, null, null, null);
    }

    /** Variante avec entité affectée et message d'erreur explicite. */
    public void signalerEvenementAudit(String action,
                                       boolean success,
                                       String errorMessage,
                                       String details,
                                       String entityType,
                                       Long entityId,
                                       String entityLabel) {
        if (action == null || action.isBlank()) return;

        ClientAuditEventRequest req = new ClientAuditEventRequest();
        req.action       = action;
        req.success      = success;
        req.errorMessage = errorMessage;
        req.details      = details;
        req.entityType   = entityType;
        req.entityId     = entityId;
        req.entityLabel  = entityLabel;
        req.hostname     = HOSTNAME;
        req.appVersion   = Config.APP_VERSION;

        AsyncRunner.run(
                () -> { client.post("/audit/client-events", req, Void.class); return null; },
                ok  -> { /* fire-and-forget */ },
                err -> log.debug("Audit client-event ignoré (réseau KO ?) action={} : {}",
                        action, err.getMessage()));
    }
}

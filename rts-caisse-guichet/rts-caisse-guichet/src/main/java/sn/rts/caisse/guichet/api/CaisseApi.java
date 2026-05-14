package sn.rts.caisse.guichet.api;

import com.fasterxml.jackson.core.type.TypeReference;
import sn.rts.caisse.guichet.model.Dto.AuthResponse;
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

    private static final CaisseApi INSTANCE = new CaisseApi();

    public static CaisseApi getInstance() {
        return INSTANCE;
    }

    private final ApiClient client = ApiClient.getInstance();

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

    /** Renvoie l'image du logo en octets, ou null si aucun logo n'a été déposé. */
    public byte[] obtenirLogoRecu() {
        try {
            return client.getBytes("/parametres/recu/logo");
        } catch (ApiException e) {
            // 404 si pas de logo
            return null;
        }
    }
}

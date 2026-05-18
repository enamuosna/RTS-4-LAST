package sn.rts.caisse.guichet.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.ApiException;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.model.Dto.BanqueDTO;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;
import sn.rts.caisse.guichet.model.Dto.CategorieDTO;
import sn.rts.caisse.guichet.model.Dto.ClientCreateRequest;
import sn.rts.caisse.guichet.model.Dto.ClientDTO;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseRequest;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseResponse;
import sn.rts.caisse.guichet.model.ModePaiement;
import sn.rts.caisse.guichet.model.TypeOperation;
import sn.rts.caisse.guichet.print.PrintRecu;
import sn.rts.caisse.guichet.print.RecuExporter;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class NouvelleOperationController {

    private static final Logger log =
            LoggerFactory.getLogger(NouvelleOperationController.class);

    // ---------------- En-tête ----------------
    @FXML private Label caisseLabel;

    // ---------------- Type d'opération ----------------
    @FXML private ToggleGroup typeGroup;
    @FXML private ToggleButton entreeToggle;
    @FXML private ToggleButton sortieToggle;

    // ---------------- Champs métier ----------------
    @FXML private ComboBox<CategorieDTO> categorieCombo;
    @FXML private ComboBox<ModePaiement> modePaiementCombo;
    @FXML private TextField montantField;
    @FXML private TextField timbreField;
    @FXML private TextField montantTtcField;
    @FXML private TextField referenceField;

    // ---------------- Banque (conditionnel) ----------------
    @FXML private VBox banqueBox;
    @FXML private ComboBox<BanqueDTO> banqueCombo;
    @FXML private Label banqueHintLabel;

    // ---------------- Client ----------------
    @FXML private ToggleGroup  clientModeGroup;
    @FXML private ToggleButton clientExistantToggle;
    @FXML private ToggleButton clientNouveauToggle;
    @FXML private VBox         clientExistantPane;
    @FXML private VBox         clientNouveauPane;
    @FXML private ComboBox<ClientDTO> clientCombo;
    @FXML private TextField nouveauClientRaisonSocialeField;
    @FXML private TextField nouveauClientIdentifiantField;
    @FXML private TextField nouveauClientTelephoneField;
    @FXML private TextField nouveauClientEmailField;
    @FXML private TextField nouveauClientAdresseField;
    @FXML private Label     nouveauClientHintLabel;

    // ---------------- Footer ----------------
    @FXML private Button enregistrerButton;

    private final CaisseApi api = CaisseApi.getInstance();
    private CaisseDTO caisse;
    private Consumer<OperationCaisseResponse> onSuccess;

    /**
     * Si non null, on est en mode <b>modification</b> : on PUT vers
     * /operations/{id} au lieu de POST. Le formulaire est pré-rempli
     * dans {@link #initialiserPourModification}.
     */
    private OperationCaisseResponse operationEnModification;

    private List<CategorieDTO> toutesCategories = List.of();
    private List<ClientDTO>    tousClients      = new ArrayList<>();
    private List<BanqueDTO>    toutesBanques    = List.of();

    // ==================================================================
    //  Initialisation
    // ==================================================================

    @FXML
    public void initialize() {
        // Modes de paiement
        modePaiementCombo.setItems(
                FXCollections.observableArrayList(ModePaiement.values()));
        modePaiementCombo.getSelectionModel().select(ModePaiement.ESPECES);
        modePaiementCombo.valueProperty().addListener(
                (obs, ancien, nouveau) -> appliquerModePaiement(nouveau));

        // Combo Banque
        banqueCombo.setConverter(new StringConverter<BanqueDTO>() {
            @Override public String toString(BanqueDTO b) {
                return b == null ? "" : (b.code + " - " + b.libelle);
            }
            @Override public BanqueDTO fromString(String s) {
                if (s == null || s.isBlank()) return null;
                String t = s.trim();
                return toutesBanques.stream()
                        .filter(b -> t.equalsIgnoreCase(b.code)
                                || t.equalsIgnoreCase(b.code + " - " + b.libelle))
                        .findFirst().orElse(null);
            }
        });

        // Combo Client
        clientCombo.setConverter(new StringConverter<ClientDTO>() {
            @Override public String toString(ClientDTO c) {
                return c != null ? c.raisonSociale : "";
            }
            @Override public ClientDTO fromString(String s) {
                if (s == null || s.isBlank()) return null;
                return tousClients.stream()
                        .filter(c -> c.raisonSociale.equalsIgnoreCase(s.trim()))
                        .findFirst().orElse(null);
            }
        });

        appliquerModeClient(false);
        appliquerModePaiement(ModePaiement.ESPECES);

        // Le timbre est CALCULE automatiquement a partir du montant HT.
        // L'utilisateur ne le saisit plus : le champ est readonly.
        timbreField.setEditable(false);
        timbreField.setFocusTraversable(false);
        timbreField.getStyleClass().add("timbre-calcule");

        // Recalcul live du timbre + montant TTC quand le montant HT change.
        montantField.textProperty().addListener((o, a, b) -> recalculerTtc());
        recalculerTtc();
    }

    /** Seuil d'application du timbre fiscal (inclusif) : 20 000 FCFA. */
    private static final BigDecimal TIMBRE_SEUIL = new BigDecimal("20000");
    /** Taux du timbre : 1% du montant HT. */
    private static final BigDecimal TIMBRE_TAUX  = new BigDecimal("0.01");

    /**
     * Calcule le timbre selon la regle RTS : 1% du montant si montant
     * &ge; 20 000 FCFA, sinon 0. Met a jour les champs Timbre + TTC.
     * Doit reproduire EXACTEMENT le calcul backend (autoritatif).
     */
    private void recalculerTtc() {
        BigDecimal montant = parseOuZero(montantField.getText());
        BigDecimal timbre = montant.compareTo(TIMBRE_SEUIL) >= 0
                ? montant.multiply(TIMBRE_TAUX).setScale(0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        timbreField.setText(timbre.signum() == 0 ? "" : Ui.formatMontant(timbre));
        BigDecimal ttc = montant.add(timbre);
        montantTtcField.setText(Ui.formatMontant(ttc));
    }

    private static BigDecimal parseOuZero(String texte) {
        BigDecimal v = Ui.parseMontant(texte);
        return v == null ? BigDecimal.ZERO : v;
    }

    public void initialiser(CaisseDTO caisse,
                            Consumer<OperationCaisseResponse> onSuccess) {
        this.caisse    = caisse;
        this.onSuccess = onSuccess;
        this.operationEnModification = null;
        if (caisse != null) {
            caisseLabel.setText(caisse.code + " · " + caisse.libelle);
        }
        chargerReferences();
        Platform.runLater(() -> montantField.requestFocus());
    }

    /**
     * Initialise le formulaire en mode <b>modification</b> : pré-remplit
     * tous les champs avec les valeurs de l'opération existante. Lors du
     * clic « Enregistrer », un PUT /operations/{id} est envoyé au lieu
     * du POST classique. Le solde de la caisse est recalculé côté backend.
     */
    public void initialiserPourModification(CaisseDTO caisse,
                                             OperationCaisseResponse op,
                                             Consumer<OperationCaisseResponse> onSuccess) {
        this.caisse    = caisse;
        this.onSuccess = onSuccess;
        this.operationEnModification = op;
        if (caisse != null) {
            caisseLabel.setText(caisse.code + " · " + caisse.libelle);
        }
        if (enregistrerButton != null) {
            enregistrerButton.setText("Mettre à jour");
        }
        // On charge d'abord les référentiels, PUIS on pré-remplit (callback)
        chargerReferencesEtPrefRemplir(op);
        Platform.runLater(() -> montantField.requestFocus());
    }

    private void chargerReferencesEtPrefRemplir(OperationCaisseResponse op) {
        // Type
        if (op.typeOperation == TypeOperation.ENTREE) entreeToggle.setSelected(true);
        else                                          sortieToggle.setSelected(true);

        // Montant + Timbre + référence
        montantField.setText(op.montant == null ? "" : op.montant.toPlainString());
        timbreField.setText(op.timbre == null
                || op.timbre.signum() == 0 ? "" : op.timbre.toPlainString());
        referenceField.setText(op.reference == null ? "" : op.reference);

        // Mode de paiement
        if (op.modePaiement != null) {
            modePaiementCombo.getSelectionModel().select(op.modePaiement);
            appliquerModePaiement(op.modePaiement);
        }

        // Categories : on charge puis on sélectionne la bonne
        AsyncRunner.run(
                () -> api.listerCategories(null),
                cats -> {
                    toutesCategories = cats == null ? List.of() : cats;
                    filtrerCategoriesPourType();
                    if (op.categorieId != null) {
                        toutesCategories.stream()
                                .filter(c -> op.categorieId.equals(c.id))
                                .findFirst()
                                .ifPresent(c -> categorieCombo.getSelectionModel().select(c));
                    }
                },
                e -> log.error("Catégories indisponibles", e));

        // Clients : on charge puis on sélectionne
        AsyncRunner.run(
                () -> api.rechercherClients(null),
                clients -> {
                    tousClients = clients == null ? new ArrayList<>() : new ArrayList<>(clients);
                    clientCombo.setItems(FXCollections.observableArrayList(tousClients));
                    if (op.clientId != null) {
                        tousClients.stream()
                                .filter(c -> op.clientId.equals(c.id))
                                .findFirst()
                                .ifPresent(c -> clientCombo.getSelectionModel().select(c));
                    }
                },
                e -> log.error("Clients indisponibles", e));

        // Banques : idem
        AsyncRunner.run(
                () -> api.listerBanques(),
                banques -> {
                    toutesBanques = banques == null ? List.of() : banques;
                    banqueCombo.setItems(FXCollections.observableArrayList(toutesBanques));
                    if (op.banqueId != null) {
                        toutesBanques.stream()
                                .filter(b -> op.banqueId.equals(b.id))
                                .findFirst()
                                .ifPresent(b -> banqueCombo.getSelectionModel().select(b));
                    }
                },
                e -> log.error("Banques indisponibles", e));
    }

    private void chargerReferences() {
        // Catégories
        AsyncRunner.run(
                () -> api.listerCategories(null),
                cats -> {
                    log.info("Catégories reçues : {}", cats == null ? 0 : cats.size());
                    toutesCategories = cats == null ? List.of() : cats;
                    filtrerCategoriesPourType();
                },
                e -> {
                    log.error("Catégories indisponibles", e);
                    Ui.erreur("Catégories indisponibles",
                            "Impossible de charger les catégories.\n\n" + describe(e));
                });

        // Clients
        AsyncRunner.run(
                () -> api.rechercherClients(null),
                clients -> {
                    log.info("Clients reçus : {}", clients == null ? 0 : clients.size());
                    tousClients = clients == null ? new ArrayList<>() : new ArrayList<>(clients);
                    clientCombo.setItems(FXCollections.observableArrayList(tousClients));
                },
                e -> {
                    log.error("Clients indisponibles", e);
                    Ui.erreur("Clients indisponibles",
                            "Impossible de charger la liste des clients.\n\n" + describe(e));
                });

        // Banques actives
        AsyncRunner.run(
                () -> api.listerBanques(),
                banques -> {
                    log.info("Banques reçues : {}", banques == null ? 0 : banques.size());
                    toutesBanques = banques == null ? List.of() : banques;
                    banqueCombo.setItems(FXCollections.observableArrayList(toutesBanques));
                },
                e -> {
                    log.error("Banques indisponibles", e);
                    Ui.erreur("Banques indisponibles",
                            "Impossible de charger la liste des banques.\n\n" + describe(e));
                });
    }

    /** Formate proprement une exception API pour l'affichage utilisateur. */
    private static String describe(Throwable t) {
        if (t == null) return "Erreur inconnue.";
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = cause.getClass().getSimpleName();
        }
        return msg;
    }

    private void filtrerCategoriesPourType() {
        TypeOperation type = getTypeSelectionne();
        List<CategorieDTO> filtrees = toutesCategories.stream()
                .filter(c -> c.actif && c.typeOperation == type)
                .toList();
        categorieCombo.setItems(FXCollections.observableArrayList(filtrees));
        if (!filtrees.isEmpty()) {
            categorieCombo.getSelectionModel().selectFirst();
        }
    }

    // ==================================================================
    //  Handlers FXML
    // ==================================================================

    @FXML
    public void onTypeChange() {
        if (typeGroup.getSelectedToggle() == null) {
            entreeToggle.setSelected(true);
        }
        filtrerCategoriesPourType();
    }

    @FXML
    public void onClientModeChange() {
        if (!clientExistantToggle.isSelected() && !clientNouveauToggle.isSelected()) {
            clientExistantToggle.setSelected(true);
        }
        appliquerModeClient(clientNouveauToggle.isSelected());
    }

    @FXML
    public void onEnregistrer() {
        OperationCaisseRequest req = construireRequeteSansClient();
        if (req == null) return;

        boolean modeNouveauClient = clientNouveauToggle.isSelected();
        ClientCreateRequest nouveauClientRequest = null;
        if (modeNouveauClient) {
            nouveauClientRequest = collecterNouveauClient();
            String erreurClient = nouveauClientRequest.validerLocalement();
            if (erreurClient != null) {
                Ui.erreur("Nouveau client", erreurClient);
                nouveauClientRaisonSocialeField.requestFocus();
                return;
            }
            nouveauClientRequest.normaliser();
        } else {
            Object rawClient = clientCombo.getValue();
            ClientDTO clientExistant = (rawClient instanceof ClientDTO dto) ? dto : null;
            req.clientId = (clientExistant != null) ? clientExistant.id : null;
        }

        enregistrerButton.setDisable(true);
        final ClientCreateRequest nouveauClientFinal = nouveauClientRequest;
        final boolean modeModification = (operationEnModification != null);
        AsyncRunner.run(
                () -> {
                    ClientDTO clientCree = null;
                    if (nouveauClientFinal != null) {
                        clientCree = api.creerClient(nouveauClientFinal);
                        req.clientId = clientCree.id;
                    }
                    OperationCaisseResponse op = modeModification
                            ? api.modifierOperation(operationEnModification.id, req)
                            : api.enregistrerOperation(req);
                    return new ResultatEnregistrement(clientCree, op);
                },
                resultat -> {
                    enregistrerButton.setDisable(false);
                    if (resultat.clientCree() != null) {
                        ajouterClientALaListeLocale(resultat.clientCree());
                    }
                    if (onSuccess != null) {
                        onSuccess.accept(resultat.operation());
                    }
                    if (modeModification) {
                        Ui.info("Opération mise à jour",
                                "Les modifications ont été enregistrées et le solde "
                                        + "de la caisse a été recalculé.");
                        fermerModal();
                    } else {
                        proposerActionsApresEnregistrement(resultat.operation());
                        fermerModal();
                    }
                },
                e -> {
                    enregistrerButton.setDisable(false);
                    afficherErreur(e);
                });
    }

    @FXML
    public void onReset() {
        montantField.clear();
        timbreField.clear();
        montantTtcField.clear();
        referenceField.clear();

        clientCombo.getSelectionModel().clearSelection();
        clientCombo.setValue(null);
        clientCombo.getEditor().clear();

        banqueCombo.getSelectionModel().clearSelection();
        banqueCombo.setValue(null);

        nouveauClientRaisonSocialeField.clear();
        nouveauClientIdentifiantField.clear();
        nouveauClientTelephoneField.clear();
        nouveauClientEmailField.clear();
        nouveauClientAdresseField.clear();

        clientExistantToggle.setSelected(true);
        appliquerModeClient(false);

        entreeToggle.setSelected(true);
        filtrerCategoriesPourType();

        modePaiementCombo.getSelectionModel().select(ModePaiement.ESPECES);
        appliquerModePaiement(ModePaiement.ESPECES);

        montantField.requestFocus();
    }

    @FXML
    public void onAnnuler() {
        fermerModal();
    }

    // ==================================================================
    //  Logique de visibilité Banque
    // ==================================================================

    private static boolean banqueRequise(ModePaiement mode) {
        return mode == ModePaiement.CHEQUE || mode == ModePaiement.VIREMENT;
    }

    private void appliquerModePaiement(ModePaiement mode) {
        boolean visible = banqueRequise(mode);
        if (banqueBox != null) {
            banqueBox.setVisible(visible);
            banqueBox.setManaged(visible);
        }
        if (!visible && banqueCombo != null) {
            banqueCombo.getSelectionModel().clearSelection();
            banqueCombo.setValue(null);
        }
        // Adapte le promptText du champ Référence selon le mode
        if (referenceField != null && mode != null) {
            switch (mode) {
                case CHEQUE       -> referenceField.setPromptText("N° du chèque");
                case VIREMENT     -> referenceField.setPromptText("N° du bordereau / IBAN");
                case WAVE         -> referenceField.setPromptText("TX Wave");
                case ORANGE_MONEY -> referenceField.setPromptText("TX Orange Money");
                case FREE_MONEY   -> referenceField.setPromptText("TX Free Money");
                case CARTE_BANCAIRE -> referenceField.setPromptText("N° d'autorisation");
                default -> referenceField.setPromptText("Référence (optionnel)");
            }
        }
    }

    // ==================================================================
    //  Helpers Client
    // ==================================================================

    private void appliquerModeClient(boolean modeNouveau) {
        if (clientExistantPane != null) {
            clientExistantPane.setVisible(!modeNouveau);
            clientExistantPane.setManaged(!modeNouveau);
        }
        if (clientNouveauPane != null) {
            clientNouveauPane.setVisible(modeNouveau);
            clientNouveauPane.setManaged(modeNouveau);
        }
        if (modeNouveau) {
            String texteCombo = clientCombo != null
                    ? clientCombo.getEditor().getText() : null;
            if (texteCombo != null && !texteCombo.isBlank()
                    && (nouveauClientRaisonSocialeField.getText() == null
                    || nouveauClientRaisonSocialeField.getText().isBlank())) {
                nouveauClientRaisonSocialeField.setText(texteCombo.trim());
            }
            Platform.runLater(nouveauClientRaisonSocialeField::requestFocus);
        } else {
            Platform.runLater(() -> {
                if (clientCombo != null) clientCombo.requestFocus();
            });
        }
    }

    private ClientCreateRequest collecterNouveauClient() {
        ClientCreateRequest req = new ClientCreateRequest();
        req.raisonSociale     = nouveauClientRaisonSocialeField.getText();
        req.identifiantFiscal = nouveauClientIdentifiantField.getText();
        req.telephone         = nouveauClientTelephoneField.getText();
        req.email             = nouveauClientEmailField.getText();
        req.adresse           = nouveauClientAdresseField.getText();
        return req;
    }

    private void ajouterClientALaListeLocale(ClientDTO clientCree) {
        if (clientCree == null) return;
        boolean dejaPresent = tousClients.stream()
                .anyMatch(c -> c.id != null && c.id.equals(clientCree.id));
        if (dejaPresent) return;
        tousClients.add(clientCree);
        clientCombo.getItems().add(clientCree);
    }

    // ==================================================================
    //  Construction de la requête
    // ==================================================================

    private OperationCaisseRequest construireRequeteSansClient() {
        if (caisse == null) {
            Ui.erreur("Aucune caisse", "Aucune caisse n'est sélectionnée.");
            return null;
        }
        CategorieDTO categorie = categorieCombo.getValue();
        if (categorie == null) {
            Ui.erreur("Catégorie manquante", "Sélectionnez une catégorie.");
            return null;
        }
        ModePaiement mode = modePaiementCombo.getValue();
        if (mode == null) {
            Ui.erreur("Mode de paiement manquant",
                    "Sélectionnez un mode de paiement.");
            return null;
        }
        BigDecimal montant = Ui.parseMontant(montantField.getText());
        if (montant == null || montant.signum() <= 0) {
            Ui.erreur("Montant invalide",
                    "Saisissez un montant strictement positif.");
            montantField.requestFocus();
            return null;
        }

        // Timbre calcule automatiquement a partir du montant HT, identique
        // a la regle backend (1% si montant >= 20 000, sinon 0). On l'envoie
        // pour information mais le backend recalcule de toute facon.
        BigDecimal timbre = montant.compareTo(TIMBRE_SEUIL) >= 0
                ? montant.multiply(TIMBRE_TAUX).setScale(0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        OperationCaisseRequest req = new OperationCaisseRequest();
        req.caisseId      = caisse.id;
        req.categorieId   = categorie.id;
        req.typeOperation = getTypeSelectionne();
        req.montant       = montant;
        req.timbre        = timbre;
        req.modePaiement  = mode;
        req.motif         = null;
        req.reference     = (referenceField.getText() == null
                || referenceField.getText().isBlank())
                ? null
                : referenceField.getText().trim();

        // Validation banque pour CHÈQUE / VIREMENT
        if (banqueRequise(mode)) {
            BanqueDTO banque = banqueCombo.getValue();
            if (banque == null || banque.id == null) {
                Ui.erreur("Banque manquante",
                        "Le règlement par " + mode.getLibelle()
                                + " nécessite la sélection d'une banque émettrice.");
                banqueCombo.requestFocus();
                return null;
            }
            req.banqueId = banque.id;
        } else {
            req.banqueId = null;
        }

        return req;
    }

    private TypeOperation getTypeSelectionne() {
        if (sortieToggle.isSelected()) return TypeOperation.SORTIE;
        return TypeOperation.ENTREE;
    }

    // ==================================================================
    //  Post-traitement
    // ==================================================================

    private void proposerActionsApresEnregistrement(OperationCaisseResponse op) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("RTS Caisse - Opération enregistrée");
        alert.setHeaderText("Reçu n° " + op.numeroRecu + " enregistré");
        alert.setContentText(
                "Montant : " + Ui.formatMontant(op.montant) + "\n"
                        + (op.clientRaisonSociale != null
                        ? "Client : " + op.clientRaisonSociale + "\n" : "")
                        + (op.banqueCode != null
                        ? "Banque : " + op.banqueCode
                        + " - " + op.banqueLibelle + "\n" : "")
                        + "\nQue souhaitez-vous faire ?");
        ButtonType btnImprimer = new ButtonType("🖨  Imprimer");
        ButtonType btnWhatsApp = new ButtonType("📱  WhatsApp");
        ButtonType btnFermer   = new ButtonType("Fermer", ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(btnImprimer, btnWhatsApp, btnFermer);
        alert.getDialogPane().setPrefWidth(420);
        Optional<ButtonType> choix = alert.showAndWait();
        if (choix.isEmpty()) return;
        ButtonType bt = choix.get();
        if (bt == btnImprimer) {
            PrintRecu.imprimer(op);
        } else if (bt == btnWhatsApp) {
            RecuExporter.envoyerWhatsApp(op);
        }
    }

    private void fermerModal() {
        Stage stage = (Stage) enregistrerButton.getScene().getWindow();
        stage.close();
    }

    private void afficherErreur(Throwable e) {
        String message;
        if (e instanceof ApiException apiEx) {
            message = apiEx.getMessage();
        } else {
            message = (e.getMessage() != null) ? e.getMessage() : "Erreur inconnue.";
        }
        log.warn("Échec enregistrement opération : {}", message, e);
        Ui.erreur("Erreur", message);
    }

    private record ResultatEnregistrement(
            ClientDTO clientCree,
            OperationCaisseResponse operation) {
    }
}
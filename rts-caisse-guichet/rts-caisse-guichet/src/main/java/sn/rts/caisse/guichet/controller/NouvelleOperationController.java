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
import javafx.scene.control.TextArea;
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
    @FXML private TextField referenceField;
    @FXML private TextArea  motifArea;

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
    }

    public void initialiser(CaisseDTO caisse,
                            Consumer<OperationCaisseResponse> onSuccess) {
        this.caisse    = caisse;
        this.onSuccess = onSuccess;
        if (caisse != null) {
            caisseLabel.setText(caisse.code + " · " + caisse.libelle);
        }
        chargerReferences();
        Platform.runLater(() -> montantField.requestFocus());
    }

    private void chargerReferences() {
        // Catégories
        AsyncRunner.run(
                () -> api.listerCategories(null),
                cats -> {
                    toutesCategories = cats;
                    filtrerCategoriesPourType();
                },
                e -> log.warn("Catégories indisponibles : {}", e.getMessage()));

        // Clients
        AsyncRunner.run(
                () -> api.rechercherClients(null),
                clients -> {
                    tousClients = new ArrayList<>(clients);
                    clientCombo.setItems(FXCollections.observableArrayList(tousClients));
                },
                e -> log.warn("Clients indisponibles : {}", e.getMessage()));

        // Banques actives
        AsyncRunner.run(
                () -> api.listerBanques(),
                banques -> {
                    toutesBanques = banques;
                    banqueCombo.setItems(FXCollections.observableArrayList(banques));
                },
                e -> log.warn("Banques indisponibles : {}", e.getMessage()));
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
        AsyncRunner.run(
                () -> {
                    ClientDTO clientCree = null;
                    if (nouveauClientFinal != null) {
                        clientCree = api.creerClient(nouveauClientFinal);
                        req.clientId = clientCree.id;
                    }
                    OperationCaisseResponse op = api.enregistrerOperation(req);
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
                    proposerActionsApresEnregistrement(resultat.operation());
                    fermerModal();
                },
                e -> {
                    enregistrerButton.setDisable(false);
                    afficherErreur(e);
                });
    }

    @FXML
    public void onReset() {
        montantField.clear();
        referenceField.clear();
        motifArea.clear();

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
        String motif = motifArea.getText();
        if (motif == null || motif.trim().isEmpty()) {
            Ui.erreur("Motif manquant", "Le motif est obligatoire.");
            motifArea.requestFocus();
            return null;
        }

        OperationCaisseRequest req = new OperationCaisseRequest();
        req.caisseId      = caisse.id;
        req.categorieId   = categorie.id;
        req.typeOperation = getTypeSelectionne();
        req.montant       = montant;
        req.modePaiement  = mode;
        req.motif         = motif.trim();
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
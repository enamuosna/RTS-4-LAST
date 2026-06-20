package sn.rts.caisse.guichet.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import sn.rts.caisse.guichet.model.Dto.TimbreConfigDto;
import sn.rts.caisse.guichet.model.ModePaiement;
import sn.rts.caisse.guichet.model.TypeOperation;
import sn.rts.caisse.guichet.print.PrintRecu;
import sn.rts.caisse.guichet.print.RecuExporter;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.RtsDialog;
import sn.rts.caisse.guichet.util.Ui;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    @FXML private VBox      timbreBox;       // masque si mode != ESPECES (sauf saisie manuelle)
    @FXML private TextField timbreField;
    @FXML private CheckBox  timbreManuelCheck;  // coché = saisie manuelle du timbre
    @FXML private TextField montantTtcField;
    @FXML private TextField referenceField;

    // ---------------- Diffusions à l'antenne (optionnel, multiples) ----------------
    // Une opération peut avoir plusieurs créneaux {date + heure + langue}.
    // Les lignes sont gérées dynamiquement dans diffusionsBox.
    @FXML private VBox    diffusionsBox;
    @FXML private Button  ajouterDiffusionButton;
    private final java.util.List<LigneDiffusion> lignesDiffusion = new ArrayList<>();
    /** Langues de diffusion chargées depuis le backend (référentiel). */
    private java.util.List<sn.rts.caisse.guichet.model.Dto.LangueDTO> langues = new ArrayList<>();

    /** Une ligne de diffusion dans l'IHM : date + heure + langue (optionnelle). */
    private static final class LigneDiffusion {
        final HBox node;
        final DatePicker date;
        final TextField heure;
        final ComboBox<sn.rts.caisse.guichet.model.Dto.LangueDTO> langue;
        LigneDiffusion(HBox node, DatePicker date, TextField heure,
                       ComboBox<sn.rts.caisse.guichet.model.Dto.LangueDTO> langue) {
            this.node = node; this.date = date; this.heure = heure; this.langue = langue;
        }
    }

    // ---------------- Justificatif (conditionnel) ----------------
    // Visible uniquement si la categorie selectionnee a
    // accepteJustificatif=true. PDF/JPG/PNG, max 5 Mo.
    @FXML private VBox       justificatifBox;
    @FXML private Button     choisirJustifButton;
    @FXML private Label      justificatifLabel;
    private File justificatifSelectionne;
    private String justificatifTypeMime;

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

    /**
     * Configuration personnalisable du timbre, chargée depuis le backend à
     * l'ouverture du formulaire. Null tant qu'elle n'est pas chargée : le
     * timbre affiché vaut alors 0 (le backend reste autoritatif au moment de
     * l'enregistrement).
     */
    private TimbreConfigDto timbreConfig;

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

        // Affiche / cache la zone d'upload du justificatif selon la
        // categorie selectionnee (flag accepteJustificatif). Recalcule aussi
        // le timbre : il peut dependre de la categorie selon la config.
        categorieCombo.valueProperty().addListener(
                (obs, ancienne, nouvelle) -> {
                    appliquerVisibiliteJustificatif(nouvelle);
                    majVisibiliteLangues();
                    recalculerTtc();
                });

        // Timbre : AUTOMATIQUE par defaut (champ readonly, recalcule live) ou
        // MANUEL si la case est cochee (le caissier saisit librement ; champ
        // vide = aucun timbre). Le timbre reste optionnel dans les deux cas.
        boolean manuelInitial = timbreManuelCheck != null && timbreManuelCheck.isSelected();
        timbreField.setEditable(manuelInitial);
        timbreField.setFocusTraversable(manuelInitial);
        timbreField.getStyleClass().add("timbre-calcule");

        if (timbreManuelCheck != null) {
            timbreManuelCheck.selectedProperty().addListener((o, a, manuel) -> {
                timbreField.setEditable(manuel);
                timbreField.setFocusTraversable(manuel);
                timbreField.clear();        // repart propre dans les deux sens
                recalculerTtc();            // auto -> remplit ; manuel -> reste vide (0)
                if (manuel) timbreField.requestFocus();
            });
        }
        // En mode manuel, recalcul du TTC a chaque frappe dans le champ timbre.
        timbreField.textProperty().addListener((o, a, b) -> {
            if (timbreManuelCheck != null && timbreManuelCheck.isSelected()) {
                recalculerTtc();
            }
        });

        // Recalcul live du timbre + montant TTC quand le montant HT change.
        montantField.textProperty().addListener((o, a, b) -> recalculerTtc());
        recalculerTtc();
    }

    private static final BigDecimal CENT = new BigDecimal("100");

    /**
     * Recalcule le timbre + le montant TTC à partir du montant HT, du mode de
     * paiement et de la catégorie sélectionnés, en appliquant la configuration
     * personnalisable du timbre ({@link #timbreConfig}). Doit reproduire
     * EXACTEMENT le calcul backend (autoritatif).
     */
    private void recalculerTtc() {
        boolean manuel = timbreManuelCheck != null && timbreManuelCheck.isSelected();
        BigDecimal montant = parseOuZero(montantField.getText());
        ModePaiement mode = modePaiementCombo != null
                ? modePaiementCombo.getValue() : null;

        BigDecimal timbre;
        if (manuel) {
            // Timbre saisi manuellement : valeur du champ (vide = aucun timbre).
            // On NE reecrit PAS le champ (l'utilisateur tape dedans).
            timbre = parseOuZero(timbreField.getText());
        } else {
            Long categorieId = (categorieCombo != null && categorieCombo.getValue() != null)
                    ? categorieCombo.getValue().id : null;
            timbre = calculerTimbre(montant, mode, categorieId);
            timbreField.setText(timbre.signum() == 0 ? "" : Ui.formatMontant(timbre));
        }

        BigDecimal ttc = montant.add(timbre);
        montantTtcField.setText(Ui.formatMontant(ttc));

        // Bloc Timbre : toujours visible en mode manuel (saisie libre quel que
        // soit le mode) ; en auto, visible seulement si la config s'applique.
        boolean afficheTimbre = manuel || timbreApplicablePourMode(mode);
        if (timbreBox != null) {
            timbreBox.setVisible(afficheTimbre);
            timbreBox.setManaged(afficheTimbre);
        }
    }

    /**
     * Indique si la configuration courante peut appliquer un timbre pour ce
     * mode de paiement (config active et mode concerné). Sert à afficher ou
     * masquer le bloc Timbre, indépendamment du montant.
     */
    private boolean timbreApplicablePourMode(ModePaiement mode) {
        TimbreConfigDto cfg = timbreConfig;
        if (cfg == null || !cfg.actif || mode == null) {
            return false;
        }
        return cfg.modesPaiement == null || cfg.modesPaiement.isEmpty()
                || cfg.modesPaiement.contains(mode.name());
    }

    /**
     * Calcule le timbre selon la configuration courante : actif, montant &ge;
     * seuil, mode concerné (liste vide = tous) et catégorie concernée (liste
     * vide = toutes). Renvoie 0 si la config n'est pas (encore) chargée.
     */
    private BigDecimal calculerTimbre(BigDecimal montant, ModePaiement mode, Long categorieId) {
        TimbreConfigDto cfg = timbreConfig;
        if (cfg == null || !cfg.actif || montant == null) {
            return BigDecimal.ZERO;
        }
        if (cfg.seuil != null && montant.compareTo(cfg.seuil) < 0) {
            return BigDecimal.ZERO;
        }
        if (cfg.modesPaiement != null && !cfg.modesPaiement.isEmpty()
                && (mode == null || !cfg.modesPaiement.contains(mode.name()))) {
            return BigDecimal.ZERO;
        }
        if (cfg.categorieIds != null && !cfg.categorieIds.isEmpty()
                && (categorieId == null || !cfg.categorieIds.contains(categorieId))) {
            return BigDecimal.ZERO;
        }
        BigDecimal taux = cfg.pourcentage == null ? BigDecimal.ZERO : cfg.pourcentage;
        return montant.multiply(taux).divide(CENT, 0, java.math.RoundingMode.HALF_UP);
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
        // Affiche / verrouille le type d'opération selon la config de la caisse.
        appliquerTypeOperationAutorise(true);
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
        // Verrouille le type selon la caisse SANS écraser le type de l'op éditée
        // (forcerDefautMixte=false : pour une caisse mixte, on garde le type
        // déjà pré-rempli depuis l'opération).
        appliquerTypeOperationAutorise(false);
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

        // Pre-remplissage de la date+heure de diffusion (si presente).
        prefRemplirDateDiffusion(op.dateDiffusion);

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

        chargerTimbreConfig();
        // Diffusions : une ligne vide au départ + chargement des langues.
        reinitialiserDiffusions();
        chargerLangues();
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
                    Ui.erreur("Produits indisponibles",
                            "Impossible de charger les produits.\n\n" + describe(e));
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

        chargerTimbreConfig();
        chargerLangues();
    }

    /**
     * Charge la configuration du timbre depuis le backend (à chaque ouverture
     * du formulaire, pour refléter immédiatement les changements de l'ADMIN).
     * En cas d'échec, on conserve {@code timbreConfig == null} : le timbre
     * affiché reste 0, le backend recalculant la valeur correcte à
     * l'enregistrement.
     */
    private void chargerTimbreConfig() {
        Long caisseId = (caisse != null) ? caisse.id : null;
        AsyncRunner.run(
                () -> api.obtenirTimbreConfig(caisseId),
                cfg -> {
                    this.timbreConfig = cfg;
                    // Caisse en mode MANUEL : on force la saisie manuelle du timbre
                    // (le caissier saisit ; champ optionnel). Case verrouillée.
                    boolean manuelCaisse = cfg != null && "MANUEL".equalsIgnoreCase(cfg.mode);
                    if (timbreManuelCheck != null) {
                        if (manuelCaisse) {
                            timbreManuelCheck.setSelected(true);
                            timbreManuelCheck.setDisable(true);
                        } else {
                            timbreManuelCheck.setDisable(false);
                        }
                    }
                    recalculerTtc();
                },
                e -> log.warn("Configuration du timbre indisponible, timbre affiché = 0 : {}",
                        describe(e)));
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

    /**
     * Adapte la section « Type d'opération » selon le type autorisé de la
     * caisse (défini par l'ADMIN) :
     * <ul>
     *   <li>{@code "ENTREE"} : seul ENCAISSEMENT est affiché et sélectionné
     *       (verrouillé) ;</li>
     *   <li>{@code "SORTIE"} : seul DÉCAISSEMENT est affiché et sélectionné
     *       (verrouillé) ;</li>
     *   <li>{@code "TOUS"} / null : les deux toggles restent disponibles
     *       (caisse mixte, choix libre du caissier).</li>
     * </ul>
     *
     * @param forcerDefautMixte si {@code true} et caisse mixte, sélectionne
     *        ENCAISSEMENT par défaut (création / reset). {@code false} en
     *        modification pour préserver le type de l'opération éditée.
     */
    private void appliquerTypeOperationAutorise(boolean forcerDefautMixte) {
        String autorise = (caisse != null && caisse.typeOperationAutorise != null)
                ? caisse.typeOperationAutorise
                : "TOUS";
        switch (autorise) {
            case "ENTREE" -> verrouillerType(entreeToggle, sortieToggle);
            case "SORTIE" -> verrouillerType(sortieToggle, entreeToggle);
            default -> {
                // Caisse mixte : les deux choix sont offerts au caissier.
                afficherToggle(entreeToggle, true);
                afficherToggle(sortieToggle, true);
                deverrouillerToggle(entreeToggle);
                deverrouillerToggle(sortieToggle);
                if (forcerDefautMixte) {
                    entreeToggle.setSelected(true);
                }
            }
        }
        filtrerCategoriesPourType();
    }

    /**
     * Verrouille la caisse sur un seul type d'opération : {@code autorise}
     * reste visible et sélectionné mais non modifiable ; {@code interdit}
     * est masqué. Le caissier voit ainsi clairement le type imposé.
     */
    private void verrouillerType(ToggleButton autorise, ToggleButton interdit) {
        afficherToggle(autorise, true);
        autorise.setSelected(true);
        autorise.setDisable(true);       // non cliquable...
        // ...mais on force l'opacité à 1 (sinon modena grise le bouton à 40%)
        // pour que le type reste parfaitement lisible par le caissier.
        autorise.setStyle("-fx-opacity: 1.0; -fx-cursor: default;");
        afficherToggle(interdit, false); // masqué
    }

    /** Rend un toggle de nouveau cliquable et retire le style de verrouillage. */
    private static void deverrouillerToggle(ToggleButton t) {
        t.setDisable(false);
        t.setStyle("");
    }

    private static void afficherToggle(ToggleButton t, boolean visible) {
        t.setVisible(visible);
        t.setManaged(visible);
    }

    /**
     * Affiche ou cache le bloc d'upload selon deux regles cumulatives :
     *  - la categorie a accepteJustificatif=true (typique AVIS ET
     *    COMMUNIQUES, APPEL : justificatif metier antenne), OU
     *  - le mode de paiement n'est pas ESPECES (justificatif de paiement
     *    electronique : cheque, virement, mobile money, carte bancaire).
     *
     * En cas de masquage, on reset le fichier eventuellement deja choisi.
     */
    private void appliquerVisibiliteJustificatif(CategorieDTO categorie) {
        ModePaiement mode = modePaiementCombo != null
                ? modePaiementCombo.getValue() : null;
        boolean autoriseParCategorie = categorie != null
                && categorie.accepteJustificatif;
        boolean autoriseParModePaiement = mode != null
                && mode != ModePaiement.ESPECES;
        boolean visible = autoriseParCategorie || autoriseParModePaiement;
        if (justificatifBox != null) {
            justificatifBox.setVisible(visible);
            justificatifBox.setManaged(visible);
        }
        if (!visible) {
            justificatifSelectionne = null;
            justificatifTypeMime = null;
            if (justificatifLabel != null) {
                justificatifLabel.setText("Aucun fichier selectionne");
            }
        }
    }

    /** Ouvre le FileChooser pour selectionner un PDF / JPG / PNG. */
    @FXML
    public void onChoisirJustificatif() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Selectionner le justificatif");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter(
                        "Documents (PDF, JPG, PNG)", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        javafx.stage.Stage stage = (javafx.stage.Stage)
                choisirJustifButton.getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        if (file.length() > 5L * 1024 * 1024) {
            Ui.erreur("Fichier trop volumineux",
                    "Le fichier fait " + (file.length() / 1024) + " Ko, max 5 Mo.");
            return;
        }
        String mime = deduireMimeJustif(file.getName());
        if (mime == null) {
            Ui.erreur("Format non supporte",
                    "Seuls les fichiers PDF, JPG et PNG sont acceptes.");
            return;
        }
        justificatifSelectionne = file;
        justificatifTypeMime = mime;
        justificatifLabel.setText(file.getName() + "  ("
                + (file.length() / 1024) + " Ko)");
    }

    private static String deduireMimeJustif(String nom) {
        String n = nom.toLowerCase();
        if (n.endsWith(".pdf"))                       return "application/pdf";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png"))                       return "image/png";
        return null;
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
        // Snapshot du justificatif eventuel (lecture du fichier en bytes
        // dans la thread UI pour eviter de tenir une reference File pendant
        // l'execution asynchrone).
        final byte[] justifBytes;
        final String justifNom;
        final String justifMime;
        if (justificatifSelectionne != null) {
            try {
                justifBytes = java.nio.file.Files.readAllBytes(
                        justificatifSelectionne.toPath());
                justifNom = justificatifSelectionne.getName();
                justifMime = justificatifTypeMime;
            } catch (Exception ex) {
                enregistrerButton.setDisable(false);
                Ui.erreur("Lecture du justificatif",
                        "Impossible de lire le fichier : " + ex.getMessage());
                return;
            }
        } else {
            justifBytes = null;
            justifNom = null;
            justifMime = null;
        }

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
                    // Upload du justificatif si selectionne. On capture les
                    // exceptions pour ne pas perdre l'operation deja creee :
                    // le caissier peut reuploader plus tard via le web.
                    if (justifBytes != null && op != null && op.id != null) {
                        try {
                            op = api.uploaderJustificatif(op.id,
                                    justifNom, justifMime, justifBytes);
                        } catch (Exception ex) {
                            log.warn("Operation {} creee mais upload justificatif "
                                    + "echoue : {}", op.numeroRecu, ex.getMessage());
                        }
                    }
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

        reinitialiserDiffusions();
        // Reset du justificatif (visibilite suit la categorie par defaut)
        justificatifSelectionne = null;
        justificatifTypeMime = null;
        if (justificatifLabel != null) {
            justificatifLabel.setText("Aucun fichier selectionne");
        }

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

        // Réinitialise le type en respectant la config de la caisse
        // (revient à ENCAISSEMENT par défaut pour une caisse mixte).
        appliquerTypeOperationAutorise(true);

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
        // La visibilité du bloc Timbre dépend désormais de la configuration
        // (modes concernés) et est gérée par recalculerTtc() ci-dessous.
        // Le justificatif depend AUSSI du mode de paiement : mode non-especes
        // = justificatif possible (preuve de paiement electronique).
        appliquerVisibiliteJustificatif(
                categorieCombo != null ? categorieCombo.getValue() : null);
        recalculerTtc();
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
            Ui.erreur("Produit manquant", "Sélectionnez un produit.");
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

        // Timbre : MANUEL (valeur saisie, null si champ vide = aucun timbre) ou
        // AUTOMATIQUE (calcule selon la config ; le backend reste autoritatif
        // pour l'auto, et respecte la valeur saisie pour le manuel).
        boolean timbreManuel = timbreManuelCheck != null && timbreManuelCheck.isSelected();
        BigDecimal timbre = timbreManuel
                ? Ui.parseMontant(timbreField.getText())
                : calculerTimbre(montant, mode, categorie.id);

        OperationCaisseRequest req = new OperationCaisseRequest();
        req.caisseId      = caisse.id;
        req.categorieId   = categorie.id;
        req.typeOperation = getTypeSelectionne();
        req.montant       = montant;
        req.timbre        = timbre;
        req.timbreManuel  = timbreManuel;
        req.modePaiement  = mode;
        req.motif         = null;
        req.reference     = (referenceField.getText() == null
                || referenceField.getText().isBlank())
                ? null
                : referenceField.getText().trim();

        // Diffusions OPTIONNELLES et multiples. collecterDiffusions renvoie
        // null si un créneau est incomplet (date sans heure, heure invalide…).
        java.util.List<sn.rts.caisse.guichet.model.Dto.DiffusionDto> diffs = collecterDiffusions();
        if (diffs == null) {
            return null;
        }
        req.diffusions = diffs;
        // dateDiffusion principale (compat reçu) = premier créneau, sinon null.
        req.dateDiffusion = diffs.isEmpty() ? null : diffs.get(0).dateHeure;

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
    //  Diffusion (date + heure)
    // ==================================================================

    private static final DateTimeFormatter HEURE_FMT =
            DateTimeFormatter.ofPattern("H:mm");

    /** Bouton « Ajouter une diffusion ». */
    @FXML
    public void onAjouterDiffusion() {
        ajouterLigneDiffusion();
    }

    /** Ajoute une ligne de diffusion (date + heure + langue) dans l'IHM. */
    private LigneDiffusion ajouterLigneDiffusion() {
        DatePicker date = new DatePicker();
        date.setPromptText("JJ/MM/AAAA");
        date.setPrefWidth(150);
        TextField heure = new TextField();
        heure.setPromptText("HH:mm");
        heure.setPrefWidth(90);
        ComboBox<sn.rts.caisse.guichet.model.Dto.LangueDTO> langue = new ComboBox<>();
        langue.setPromptText("Langue (optionnel)");
        langue.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(langue, Priority.ALWAYS);
        peuplerCombo(langue);
        Button suppr = new Button("✕");
        suppr.getStyleClass().addAll("button", "button-ghost");

        HBox row = new HBox(8, date, heure, langue, suppr);
        LigneDiffusion ligne = new LigneDiffusion(row, date, heure, langue);
        suppr.setOnAction(e -> supprimerLigneDiffusion(ligne));
        lignesDiffusion.add(ligne);
        if (diffusionsBox != null) {
            diffusionsBox.getChildren().add(row);
        }
        majVisibiliteLanguesLigne(ligne);
        return ligne;
    }

    private void supprimerLigneDiffusion(LigneDiffusion ligne) {
        lignesDiffusion.remove(ligne);
        if (diffusionsBox != null) {
            diffusionsBox.getChildren().remove(ligne.node);
        }
        if (lignesDiffusion.isEmpty()) {
            ajouterLigneDiffusion(); // toujours laisser au moins une ligne vide
        }
    }

    /** Remet la liste des diffusions à une unique ligne vide. */
    private void reinitialiserDiffusions() {
        lignesDiffusion.clear();
        if (diffusionsBox != null) {
            diffusionsBox.getChildren().clear();
        }
        ajouterLigneDiffusion();
    }

    /** Charge le référentiel des langues et (re)peuple les combos existants. */
    private void chargerLangues() {
        AsyncRunner.run(
                api::listerLangues,
                list -> {
                    this.langues = list == null ? new ArrayList<>() : list;
                    for (LigneDiffusion l : lignesDiffusion) {
                        peuplerCombo(l.langue);
                    }
                },
                e -> log.warn("Langues de diffusion indisponibles : {}", describe(e)));
    }

    /** Remplit un combo avec « Aucune » + les langues actives, en conservant la sélection. */
    private void peuplerCombo(ComboBox<sn.rts.caisse.guichet.model.Dto.LangueDTO> combo) {
        sn.rts.caisse.guichet.model.Dto.LangueDTO aucune =
                new sn.rts.caisse.guichet.model.Dto.LangueDTO();
        aucune.libelle = "— Aucune —";
        java.util.List<sn.rts.caisse.guichet.model.Dto.LangueDTO> items = new ArrayList<>();
        items.add(aucune);
        items.addAll(langues);
        Long selId = combo.getValue() != null ? combo.getValue().id : null;
        combo.setItems(FXCollections.observableArrayList(items));
        if (selId != null) {
            for (var l : items) {
                if (selId.equals(l.id)) { combo.setValue(l); break; }
            }
        }
    }

    /** Affiche/masque le combo langue de chaque ligne selon le produit choisi. */
    private void majVisibiliteLangues() {
        for (LigneDiffusion l : lignesDiffusion) {
            majVisibiliteLanguesLigne(l);
        }
    }

    private void majVisibiliteLanguesLigne(LigneDiffusion l) {
        boolean propose = categorieCombo != null && categorieCombo.getValue() != null
                && categorieCombo.getValue().proposeLangue;
        l.langue.setVisible(propose);
        l.langue.setManaged(propose);
        if (!propose) {
            l.langue.setValue(null);
        }
    }

    /**
     * Collecte les créneaux de diffusion saisis. Lignes vides ignorées
     * (diffusion optionnelle) ; ligne incomplète/heure invalide → erreur + null.
     */
    private java.util.List<sn.rts.caisse.guichet.model.Dto.DiffusionDto> collecterDiffusions() {
        java.util.List<sn.rts.caisse.guichet.model.Dto.DiffusionDto> result = new ArrayList<>();
        for (LigneDiffusion l : lignesDiffusion) {
            forcerCommitDatePicker(l.date);
            LocalDate date = l.date.getValue();
            String heureTexte = l.heure.getText();
            boolean heureRenseignee = heureTexte != null && !heureTexte.isBlank();

            if (date == null && !heureRenseignee) {
                continue; // ligne vide : ignorée (diffusion optionnelle)
            }
            if (date == null) {
                Ui.erreur("Date de diffusion manquante",
                        "Une diffusion a une heure sans date. Complétez la date ou videz la ligne.");
                l.date.requestFocus();
                return null;
            }
            if (!heureRenseignee) {
                Ui.erreur("Heure de diffusion manquante",
                        "Une diffusion a une date sans heure (format HH:mm, ex. 20:30).");
                l.heure.requestFocus();
                return null;
            }
            LocalTime heure;
            try {
                heure = LocalTime.parse(heureTexte.trim(), HEURE_FMT);
            } catch (Exception ex) {
                Ui.erreur("Heure invalide",
                        "L'heure de diffusion doit être au format HH:mm (ex. 20:30). "
                                + "Valeur : " + heureTexte);
                l.heure.requestFocus();
                return null;
            }
            Long langueId = (l.langue.isVisible() && l.langue.getValue() != null)
                    ? l.langue.getValue().id : null;
            result.add(new sn.rts.caisse.guichet.model.Dto.DiffusionDto(
                    LocalDateTime.of(date, heure), langueId));
        }
        return result;
    }

    /**
     * Force le DatePicker a valider le texte saisi dans son editeur en
     * appelant son converter. Sans ca, {@code getValue()} reste null si
     * l'utilisateur a juste tape la date sans appuyer sur Entree.
     */
    private static void forcerCommitDatePicker(DatePicker picker) {
        try {
            String texte = picker.getEditor().getText();
            if (texte == null || texte.isBlank()) {
                return;
            }
            // Le DatePicker JavaFX a un StringConverter par defaut au format
            // local. On l'utilise pour parser le texte saisi.
            LocalDate parse = picker.getConverter().fromString(texte);
            picker.setValue(parse);
        } catch (Exception ignored) {
            // Format invalide : on laisse getValue() retourner null, et la
            // validation aval affichera l'erreur appropriee.
        }
    }

    /** Pré-remplit les diffusions depuis une opération existante (modification). */
    private void prefRemplirDateDiffusion(LocalDateTime dt) {
        reinitialiserDiffusions();
        if (dt != null && !lignesDiffusion.isEmpty()) {
            LigneDiffusion l = lignesDiffusion.get(0);
            l.date.setValue(dt.toLocalDate());
            l.heure.setText(String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
        }
    }

    // ==================================================================
    //  Post-traitement
    // ==================================================================

    private void proposerActionsApresEnregistrement(OperationCaisseResponse op) {
        String message = "Montant : " + Ui.formatMontant(op.montant) + "\n"
                + (op.clientRaisonSociale != null
                ? "Client : " + op.clientRaisonSociale + "\n" : "")
                + (op.banqueCode != null
                ? "Banque : " + op.banqueCode + " - " + op.banqueLibelle + "\n" : "")
                + "\nQue souhaitez-vous faire ?";

        Optional<String> choix = RtsDialog.<String>create()
                .type(RtsDialog.Type.SUCCESS)
                .title("Reçu n° " + op.numeroRecu + " enregistré")
                .message(message)
                .width(440)
                .cancelValue(null)
                .defaultButton("🖨  Imprimer", RtsDialog.ButtonKind.PRIMARY, "print")
                .button("📱  WhatsApp", RtsDialog.ButtonKind.SUCCESS, "whatsapp")
                .cancelButton("Fermer", RtsDialog.ButtonKind.SECONDARY, null)
                .showAndWait();

        if (choix.isEmpty()) return;
        switch (choix.get()) {
            case "print"    -> PrintRecu.imprimer(op);
            case "whatsapp" -> RecuExporter.envoyerWhatsApp(op);
            default         -> { /* Fermer */ }
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
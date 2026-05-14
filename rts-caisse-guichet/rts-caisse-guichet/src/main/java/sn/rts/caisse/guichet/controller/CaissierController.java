package sn.rts.caisse.guichet.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.ApiException;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.app.GuichetApplication;
import sn.rts.caisse.guichet.model.Dto.AuthResponse;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;
import sn.rts.caisse.guichet.model.Dto.JournalCaisseResponse;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseResponse;
import sn.rts.caisse.guichet.model.StatutCaisse;
import sn.rts.caisse.guichet.model.TypeOperation;
import sn.rts.caisse.guichet.print.PrintRecu;
import sn.rts.caisse.guichet.print.RecuExporter;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Session;
import sn.rts.caisse.guichet.util.Ui;
import javafx.scene.shape.SVGPath;
import sn.rts.caisse.guichet.util.ThemeManager;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dashboard caissier — version refondue v3.
 *
 * <h2>Évolution v3</h2>
 * <ul>
 *   <li>Le bouton <b>"Changer de caisse"</b> a été retiré : un seul caissier
 *       par caisse, le choix se fait à la connexion.</li>
 *   <li>Les opérations du jour sont triées par <b>date décroissante</b> :
 *       la dernière opération apparaît toujours en haut du tableau.</li>
 *   <li>Un <b>champ de recherche automatique</b> filtre la liste en temps
 *       réel sur le n° de reçu, le motif, la catégorie, le client,
 *       le type et le montant.</li>
 *   <li>Une <b>pagination</b> de 10 opérations par page facilite la
 *       navigation lorsque la journée est chargée (boutons «, ‹, ›, »).</li>
 * </ul>
 *
 * <p>L'enregistrement d'une nouvelle opération se fait via le bouton
 * "+ Nouvelle opération" qui ouvre le modal dédié. Au retour du modal
 * (succès), la liste est rafraîchie automatiquement.</p>
 */
public class CaissierController {

    private static final Logger log = LoggerFactory.getLogger(CaissierController.class);

    /** Largeur minimale garantie pour la colonne Actions (3 boutons). */
    private static final double LARGEUR_MIN_COLONNE_ACTIONS = 150.0;

    /** Taille des boutons d'action (icônes carrées). */
    private static final double TAILLE_BOUTON_ACTION = 30.0;

    /** Nombre d'opérations affichées par page de pagination. */
    private static final int TAILLE_PAGE = 10;

    // ================ Header ================
    @FXML private Label caisseHeaderLabel;
    @FXML private Label agentInitialsLabel;
    @FXML private Label agentNameLabel;
    @FXML private Label agentRoleLabel;

    // ================ Bannière statut ================
    @FXML private HBox statusBanner;
    @FXML private Label statusLabel;
    @FXML private VBox soldeBox;
    @FXML private Label soldeLabel;
    @FXML private Button ouvrirButton;
    @FXML private Button cloturerButton;

    // ================ Zone opérations ================
    @FXML private Button nouvelleOperationButton;
    @FXML private Button refreshButton;
    @FXML private TextField searchField;

    @FXML private TableView<OperationCaisseResponse> operationsTable;
    @FXML private TableColumn<OperationCaisseResponse, String> colHeure;
    @FXML private TableColumn<OperationCaisseResponse, String> colNumero;
    @FXML private TableColumn<OperationCaisseResponse, String> colType;
    @FXML private TableColumn<OperationCaisseResponse, String> colCategorie;
    @FXML private TableColumn<OperationCaisseResponse, String> colMotif;
    @FXML private TableColumn<OperationCaisseResponse, String> colMontant;
    @FXML private TableColumn<OperationCaisseResponse, OperationCaisseResponse> colActions;
    @FXML private Label compteurLabel;

    // ================ Pagination ================
    @FXML private Button firstPageButton;
    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button lastPageButton;
    @FXML private Label  pageInfoLabel;

    //BONUS
    @FXML private Button   themeToggleButton;
    @FXML private SVGPath  themeToggleIcon;

    // ================ État ================

    private final CaisseApi api = CaisseApi.getInstance();

    /** Liste maître : toutes les opérations du jour, triées par date desc. */
    private final ObservableList<OperationCaisseResponse> operationsCompletes =
            FXCollections.observableArrayList();

    /** Vue filtrée par le champ de recherche. */
    private final FilteredList<OperationCaisseResponse> operationsFiltrees =
            new FilteredList<>(operationsCompletes, op -> true);

    /** Liste affichée dans le TableView : la page courante uniquement. */
    private final ObservableList<OperationCaisseResponse> operationsPage =
            FXCollections.observableArrayList();

    /** Index de la page courante (0-indexé). */
    private int pageActuelle = 0;

    // ================================================================
    //  Initialisation
    // ================================================================

    @FXML
    public void initialize() {
        AuthResponse auth = Session.getInstance().getAuth();
        if (auth != null) {
            agentNameLabel.setText(auth.nomComplet);
            agentRoleLabel.setText(auth.matricule + " · " + auth.role.name());
            agentInitialsLabel.setText(initialesDe(auth.nomComplet));
        }

        configurerTable();
        operationsTable.setItems(operationsPage);

        configurerRecherche();
        configurerPagination();

        if (Session.getInstance().getCaisseActive() == null) {
            Platform.runLater(() -> GuichetApplication.goTo("selection-caisse.fxml"));
            return;
        }

        updateThemeIcon();
        ThemeManager.getInstance().addChangeListener(this::updateThemeIcon);

        rafraichirCaisse();
    }

    @FXML
    private void onToggleTheme() {
        ThemeManager.getInstance().toggle();
        // updateThemeIcon() sera appelé automatiquement via le listener
    }

    private void updateThemeIcon() {
        if (themeToggleIcon == null) return;
        boolean dark = ThemeManager.getInstance().isDark();
        if (dark) {
            // Mode sombre actif → afficher un soleil (clic = basculer en clair)
            themeToggleIcon.setContent(
                    "M12 1 v2 m0 18 v2 M4.22 4.22 l1.42 1.42 m12.72 12.72 l1.42 1.42 "
                            + "M1 12 h2 m18 0 h2 M4.22 19.78 l1.42-1.42 m12.72-12.72 l1.42-1.42 "
                            + "M12 7 a5 5 0 1 0 0 10 5 5 0 0 0 0-10 z"
            );
            if (themeToggleButton != null) {
                themeToggleButton.setTooltip(new javafx.scene.control.Tooltip(
                        "Passer en mode clair"));
            }
        } else {
            // Mode clair actif → afficher une lune (clic = basculer en sombre)
            themeToggleIcon.setContent(
                    "M21 12.79 A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79 z"
            );
            if (themeToggleButton != null) {
                themeToggleButton.setTooltip(new javafx.scene.control.Tooltip(
                        "Passer en mode sombre"));
            }
        }
    }

    private static String initialesDe(String nomComplet) {
        if (nomComplet == null || nomComplet.isBlank()) return "—";
        String[] parts = nomComplet.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }

    // ================================================================
    //  Configuration TableView
    // ================================================================

    private void configurerTable() {
        colHeure.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().dateOperation != null
                        ? c.getValue().dateOperation.toLocalTime().withNano(0).toString().substring(0, 5)
                        : ""));
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().numeroRecu));
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().typeOperation.name()));
        colCategorie.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categorieLibelle));
        colMotif.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().motif));
        colMontant.setCellValueFactory(c -> new SimpleStringProperty(Ui.formatMontant(c.getValue().montant)));
        colActions.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));

        colActions.setMinWidth(LARGEUR_MIN_COLONNE_ACTIONS);
        colActions.setPrefWidth(LARGEUR_MIN_COLONNE_ACTIONS);

        // Colonne type : badge coloré
        colType.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("ENTREE".equals(item) ? "ENTRÉE" : "SORTIE");
                    boolean entree = "ENTREE".equals(item);
                    setStyle("-fx-text-fill: " + (entree ? "#16a34a" : "#dc2626")
                            + "; -fx-font-weight: bold;");
                }
            }
        });

        // Colonne montant : couleur + barré si annulée
        colMontant.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                OperationCaisseResponse op = getTableRow() == null ? null : getTableRow().getItem();
                if (op == null) return;
                String couleur = op.typeOperation == TypeOperation.ENTREE ? "#16a34a" : "#dc2626";
                String style = "-fx-text-fill: " + couleur + "; -fx-font-weight: bold;";
                if (op.annulee) {
                    style += " -fx-strikethrough: true; -fx-opacity: 0.5;";
                }
                setStyle(style);
            }
        });

        // Colonne actions : Imprimer + WhatsApp + Annuler
        colActions.setCellFactory(column -> new TableCell<>() {
            private static final String SVG_PRINT =
                    "M6 9 V2 h12 v7 M6 18 H4 a2 2 0 0 1-2-2 v-5 a2 2 0 0 1 2-2 h16 a2 2 0 0 1 2 2 v5 a2 2 0 0 1-2 2 h-2 M6 14 h12 v8 H6 z";
            private static final String SVG_CANCEL =
                    "M18 6 L6 18 M6 6 l12 12";
            private static final String SVG_WHATSAPP =
                    "M21 11.5 a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9 L3 21 l1.9-5.7 a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9 h.5 a8.48 8.48 0 0 1 8 8 v.5 z";

            private final Button btnImprimer = new Button();
            private final Button btnWhatsApp = new Button();
            private final Button btnAnnuler  = new Button();
            private final HBox   box         = new HBox(4, btnImprimer, btnWhatsApp, btnAnnuler);

            {
                SVGPath iconPrint = new SVGPath();
                iconPrint.setContent(SVG_PRINT);
                iconPrint.setScaleX(0.6); iconPrint.setScaleY(0.6);
                iconPrint.setStyle("-fx-fill: transparent; -fx-stroke: #475569; "
                        + "-fx-stroke-width: 2; -fx-stroke-line-cap: round; -fx-stroke-line-join: round;");
                btnImprimer.setGraphic(iconPrint);
                btnImprimer.getStyleClass().addAll("button", "button-icon");
                btnImprimer.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnImprimer.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnImprimer.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnImprimer.setTooltip(new javafx.scene.control.Tooltip("Réimprimer le reçu"));

                SVGPath iconWa = new SVGPath();
                iconWa.setContent(SVG_WHATSAPP);
                iconWa.setScaleX(0.6); iconWa.setScaleY(0.6);
                iconWa.setStyle("-fx-fill: transparent; -fx-stroke: #25D366; "
                        + "-fx-stroke-width: 2; -fx-stroke-line-cap: round; -fx-stroke-line-join: round;");
                btnWhatsApp.setGraphic(iconWa);
                btnWhatsApp.getStyleClass().addAll("button", "button-icon");
                btnWhatsApp.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnWhatsApp.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnWhatsApp.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnWhatsApp.setTooltip(new javafx.scene.control.Tooltip(
                        "Envoyer le reçu par WhatsApp"));

                SVGPath iconCancel = new SVGPath();
                iconCancel.setContent(SVG_CANCEL);
                iconCancel.setScaleX(0.65); iconCancel.setScaleY(0.65);
                iconCancel.setStyle("-fx-fill: transparent; -fx-stroke: #dc2626; "
                        + "-fx-stroke-width: 2.5; -fx-stroke-line-cap: round;");
                btnAnnuler.setGraphic(iconCancel);
                btnAnnuler.getStyleClass().addAll("button", "button-icon");
                btnAnnuler.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnAnnuler.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnAnnuler.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnAnnuler.setTooltip(new javafx.scene.control.Tooltip("Annuler l'opération"));

                box.setAlignment(javafx.geometry.Pos.CENTER);
            }

            @Override
            protected void updateItem(OperationCaisseResponse op, boolean empty) {
                super.updateItem(op, empty);
                if (empty || op == null) {
                    setGraphic(null);
                    return;
                }
                btnImprimer.setOnAction(e -> PrintRecu.imprimer(op));
                btnWhatsApp.setOnAction(e -> RecuExporter.envoyerWhatsApp(op));
                btnAnnuler.setOnAction(e -> annulerOperation(op));

                btnWhatsApp.setDisable(false);
                btnWhatsApp.setOpacity(1.0);

                btnAnnuler.setDisable(op.annulee);
                btnAnnuler.setOpacity(op.annulee ? 0.3 : 1.0);
                setGraphic(box);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
    }

    // ================================================================
    //  Configuration recherche + pagination
    // ================================================================

    /**
     * Branche le champ de recherche sur la liste filtrée.
     * Le filtrage est insensible à la casse et porte sur les colonnes
     * les plus utiles (n° reçu, motif, catégorie, client, type, mode
     * de paiement, montant).
     */
    private void configurerRecherche() {
        searchField.textProperty().addListener((obs, ancien, nouveau) -> {
            String filtre = nouveau == null ? "" : nouveau.trim().toLowerCase();
            operationsFiltrees.setPredicate(op -> {
                if (filtre.isEmpty()) return true;
                if (op == null) return false;

                if (contient(op.numeroRecu, filtre))           return true;
                if (contient(op.motif, filtre))                return true;
                if (contient(op.categorieLibelle, filtre))     return true;
                if (contient(op.clientRaisonSociale, filtre))  return true;
                if (contient(op.clientTelephone, filtre))      return true;
                if (op.typeOperation != null
                        && contient(op.typeOperation.name(), filtre))   return true;
                if (op.modePaiement != null
                        && contient(op.modePaiement.name(), filtre))    return true;
                if (op.montant != null
                        && op.montant.toPlainString().contains(filtre)) return true;

                return false;
            });
            // Toute frappe ramène l'utilisateur à la première page de résultats
            pageActuelle = 0;
            rafraichirPage();
        });
    }

    /**
     * Réagit aux variations de la liste filtrée : insertions, suppressions
     * (ex. annulation d'opération, refresh, nouvelle opération enregistrée).
     */
    private void configurerPagination() {
        operationsFiltrees.addListener(
                (ListChangeListener<OperationCaisseResponse>) c -> rafraichirPage());
        // Initialisation : tout est vide au démarrage
        rafraichirPage();
    }

    private static boolean contient(String source, String filtre) {
        if (source == null) return false;
        return source.toLowerCase().contains(filtre);
    }

    /**
     * Recalcule la page courante et met à jour les contrôles de navigation
     * ainsi que le compteur. À appeler à chaque fois que le contenu
     * filtré change ou que l'utilisateur change de page.
     */
    private void rafraichirPage() {
        int totalFiltre = operationsFiltrees.size();
        int totalGlobal = operationsCompletes.size();
        int nbPages     = Math.max(1, (int) Math.ceil(totalFiltre / (double) TAILLE_PAGE));

        // Borne l'index courant (utile après un filtre ou une suppression)
        if (pageActuelle >= nbPages) pageActuelle = nbPages - 1;
        if (pageActuelle < 0)        pageActuelle = 0;

        int from = pageActuelle * TAILLE_PAGE;
        int to   = Math.min(from + TAILLE_PAGE, totalFiltre);

        if (totalFiltre == 0) {
            operationsPage.clear();
        } else {
            operationsPage.setAll(operationsFiltrees.subList(from, to));
        }

        // Libellé de page
        pageInfoLabel.setText("Page " + (pageActuelle + 1) + " / " + nbPages);

        // État des boutons de navigation
        boolean premiere = pageActuelle == 0;
        boolean derniere = pageActuelle >= nbPages - 1;
        firstPageButton.setDisable(premiere);
        prevPageButton .setDisable(premiere);
        nextPageButton .setDisable(derniere);
        lastPageButton .setDisable(derniere);

        // Compteur : indique si un filtre est actif
        if (totalFiltre == totalGlobal) {
            compteurLabel.setText(totalGlobal + " opération" + (totalGlobal > 1 ? "s" : ""));
        } else {
            compteurLabel.setText(totalFiltre + " / " + totalGlobal + " opération"
                    + (totalGlobal > 1 ? "s" : "") + " (filtré)");
        }
    }

    // ================================================================
    //  Handlers pagination
    // ================================================================

    @FXML
    public void onPremierePage() {
        pageActuelle = 0;
        rafraichirPage();
    }

    @FXML
    public void onPagePrecedente() {
        if (pageActuelle > 0) {
            pageActuelle--;
            rafraichirPage();
        }
    }

    @FXML
    public void onPageSuivante() {
        int nbPages = Math.max(1,
                (int) Math.ceil(operationsFiltrees.size() / (double) TAILLE_PAGE));
        if (pageActuelle < nbPages - 1) {
            pageActuelle++;
            rafraichirPage();
        }
    }

    @FXML
    public void onDernierePage() {
        int nbPages = Math.max(1,
                (int) Math.ceil(operationsFiltrees.size() / (double) TAILLE_PAGE));
        pageActuelle = nbPages - 1;
        rafraichirPage();
    }

    // ================================================================
    //  Chargement de l'état
    // ================================================================

    private void rafraichirCaisse() {
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null) return;

        AsyncRunner.run(
                () -> api.obtenirCaisse(caisse.id),
                this::appliquerEtatCaisse,
                e -> Ui.erreur("Erreur", "Impossible de charger la caisse : " + e.getMessage()));
    }

    private void appliquerEtatCaisse(CaisseDTO caisse) {
        Session.getInstance().setCaisseActive(caisse);
        caisseHeaderLabel.setText(caisse.code + " · " + caisse.libelle);

        boolean ouverte   = caisse.statut == StatutCaisse.OUVERTE;
        boolean suspendue = caisse.statut == StatutCaisse.SUSPENDUE;

        statusBanner.getStyleClass().removeAll("ouverte", "fermee");
        statusBanner.getStyleClass().add(ouverte ? "ouverte" : "fermee");

        if (suspendue) {
            statusLabel.setText("SUSPENDUE");
        } else {
            statusLabel.setText(ouverte ? "OUVERTE" : "FERMÉE");
        }

        soldeBox.setVisible(ouverte);
        soldeBox.setManaged(ouverte);
        soldeLabel.setText(Ui.formatMontant(caisse.soldeCourant));

        ouvrirButton.setVisible(!ouverte && !suspendue);
        ouvrirButton.setManaged(!ouverte && !suspendue);
        cloturerButton.setVisible(ouverte);
        cloturerButton.setManaged(ouverte);

        // Le bouton "Nouvelle opération" n'est actif que si la caisse est ouverte
        nouvelleOperationButton.setDisable(!ouverte);

        if (ouverte) {
            chargerOperationsDuJour();
        } else {
            operationsCompletes.clear();
            pageActuelle = 0;
            rafraichirPage();
        }
    }

    /**
     * Charge les opérations du jour depuis le backend, les <b>trie par
     * date décroissante</b> (la plus récente en premier) puis met à jour
     * la liste maître. Les listeners de FilteredList et de pagination
     * répercutent automatiquement le changement vers le tableau visible.
     */
    private void chargerOperationsDuJour() {
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null) return;

        AsyncRunner.run(
                () -> api.operationsDuJour(caisse.id),
                ops -> {
                    List<OperationCaisseResponse> triees = ops == null
                            ? List.of()
                            : ops.stream()
                                 .sorted(Comparator.comparing(
                                         (OperationCaisseResponse o) -> o.dateOperation,
                                         Comparator.nullsLast(Comparator.reverseOrder())))
                                 .toList();
                    operationsCompletes.setAll(triees);
                    // setAll declenche le listener FilteredList -> rafraichirPage()
                    // mais on rappelle explicitement pour reinitialiser l'index.
                    pageActuelle = 0;
                    rafraichirPage();
                },
                e -> log.warn("Opérations indisponibles : {}", e.getMessage()));
    }

    // ================================================================
    //  Actions principales
    // ================================================================

    @FXML
    public void onOuvrirCaisse() {
        Optional<String> input = Ui.demanderTexte(
                "Ouverture de caisse",
                "Montant du fond d'ouverture (FCFA) :",
                "0");
        if (input.isEmpty()) return;

        BigDecimal fond = Ui.parseMontant(input.get());
        if (fond == null || fond.signum() < 0) {
            Ui.erreur("Montant invalide", "Saisissez un montant positif ou nul.");
            return;
        }

        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        AsyncRunner.run(
                () -> api.ouvrirCaisse(caisse.id, fond),
                journal -> {
                    // api.ouvrirCaisse retourne un JournalCaisseResponse (journal du jour)
                    // qui ne porte pas le code caisse : on utilise la CaisseDTO du scope.
                    Ui.info("Caisse ouverte",
                            "La caisse " + caisse.code + " est maintenant ouverte avec un fond de "
                                    + Ui.formatMontant(fond) + ".");
                    rafraichirCaisse();
                },
                this::afficherErreur);
    }

    @FXML
    public void onCloturerCaisse() {
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null) return;

        Optional<String> inputSolde = Ui.demanderTexte(
                "Clôture de caisse",
                "Solde réellement compté en caisse (FCFA) :\n\n"
                        + "Solde théorique attendu : " + Ui.formatMontant(caisse.soldeCourant),
                caisse.soldeCourant != null ? caisse.soldeCourant.toPlainString() : "0");
        if (inputSolde.isEmpty()) return;

        BigDecimal reel = Ui.parseMontant(inputSolde.get());
        if (reel == null || reel.signum() < 0) {
            Ui.erreur("Montant invalide", "Saisissez un montant positif ou nul.");
            return;
        }

        Optional<String> inputCommentaire = Ui.demanderTexte(
                "Clôture de caisse",
                "Commentaire (facultatif, écart éventuel) :",
                "");
        String commentaire = inputCommentaire.orElse("");

        AsyncRunner.run(
                () -> api.cloturerCaisse(caisse.id, reel, commentaire),
                this::afficherResultatCloture,
                this::afficherErreur);
    }

    private void afficherResultatCloture(JournalCaisseResponse journal) {
        BigDecimal ecart = journal.ecart != null ? journal.ecart : BigDecimal.ZERO;
        String resume = "Clôture effectuée avec succès.\n\n"
                + "Fond d'ouverture : " + Ui.formatMontant(journal.fondOuverture) + "\n"
                + "Total entrées    : " + Ui.formatMontant(journal.totalEntrees) + "\n"
                + "Total sorties    : " + Ui.formatMontant(journal.totalSorties) + "\n"
                + "Solde théorique  : " + Ui.formatMontant(journal.soldeTheorique) + "\n"
                + "Solde réel       : " + Ui.formatMontant(journal.soldeReel) + "\n"
                + "─────────────────────────\n"
                + "ÉCART            : " + Ui.formatMontant(ecart) + "\n\n";

        if (ecart.signum() == 0) {
            resume += "✓ Aucun écart — clôture parfaite.";
        } else if (ecart.signum() > 0) {
            resume += "⚠ Excédent de caisse : " + Ui.formatMontant(ecart);
        } else {
            resume += "⚠ Manquant en caisse : " + Ui.formatMontant(ecart.abs());
        }
        resume += "\n\nLa clôture reste à valider par un superviseur.";

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("RTS Caisse - Clôture");
        alert.setHeaderText("Caisse " + journal.caisseLibelle + " clôturée");
        alert.setContentText(resume);
        alert.getDialogPane().setPrefWidth(480);
        alert.showAndWait();

        rafraichirCaisse();
    }

    /**
     * Ouvre le modal de saisie d'une nouvelle opération.
     *
     * <p>Le modal est créé en {@link Modality#APPLICATION_MODAL} pour bloquer
     * la fenêtre principale pendant la saisie. Au retour (clic Enregistrer
     * réussi), un callback rafraîchit la liste des opérations du jour.</p>
     */
    @FXML
    public void onNouvelleOperation() {
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null || caisse.statut != StatutCaisse.OUVERTE) {
            Ui.erreur("Caisse fermée",
                    "Vous devez d'abord ouvrir la caisse pour saisir une opération.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    GuichetApplication.class.getResource("/fxml/nouvelle-operation.fxml"));
            Parent root = loader.load();
            NouvelleOperationController controller = loader.getController();

            Stage modal = new Stage();
            modal.initOwner(getCurrentWindow());
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.initStyle(StageStyle.UTILITY);
            modal.setTitle("Nouvelle opération de caisse");
            modal.setResizable(false);

            Scene scene = new Scene(root);

            // Enregistre la scène dans le ThemeManager :
            // - applique immédiatement le CSS du thème actuel (dark ou light)
            // - la scène suivra automatiquement tous les basculements light/dark
            ThemeManager.getInstance().register(scene);
            // Nettoie la référence dès que la fenêtre est fermée (anti-fuite mémoire)
            modal.setOnHidden(e -> ThemeManager.getInstance().unregister(scene));

            modal.setScene(scene);

            // Initialise le contrôleur avec la caisse + le callback
            controller.initialiser(caisse, op -> {
                // Le contrôleur du modal nous prévient qu'une opération a été créée
                // → on rafraîchit le solde + la liste sur le dashboard.
                rafraichirCaisse();
            });

            modal.showAndWait();

        } catch (Exception e) {
            log.error("Échec ouverture du modal Nouvelle opération : {}", e.getMessage(), e);
            Ui.erreur("Erreur",
                    "Impossible d'ouvrir le formulaire de saisie : " + e.getMessage());
        }
    }

    /** Récupère la fenêtre courante (parent du modal) à partir d'un nœud connu. */
    private Window getCurrentWindow() {
        if (operationsTable != null && operationsTable.getScene() != null) {
            return operationsTable.getScene().getWindow();
        }
        return null;
    }

    @FXML
    public void onRefresh() {
        rafraichirCaisse();
    }

    private void annulerOperation(OperationCaisseResponse op) {
        Optional<String> motif = Ui.demanderTexte(
                "Annulation d'opération",
                "Motif de l'annulation (N° " + op.numeroRecu + ") :",
                "");
        if (motif.isEmpty() || motif.get().isBlank()) return;

        AsyncRunner.run(
                () -> api.annulerOperation(op.id, motif.get()),
                annulee -> {
                    Ui.info("Opération annulée",
                            "Le reçu n° " + annulee.numeroRecu
                                    + " a été annulé. Le solde a été mis à jour par contre-passation.");
                    rafraichirCaisse();
                },
                this::afficherErreur);
    }

    // ================================================================
    //  Header : déconnexion
    //  (le handler "onChangerCaisse" a été retiré : un seul caissier
    //   par caisse, la sélection se fait à la connexion)
    // ================================================================

    @FXML
    public void onLogout() {
        CaisseDTO active = Session.getInstance().getCaisseActive();
        if (active != null && active.statut == StatutCaisse.OUVERTE) {
            boolean confirm = Ui.confirmer(
                    "Caisse ouverte",
                    "La caisse " + active.code + " est encore ouverte.\n\n"
                            + "Se déconnecter quand même ?");
            if (!confirm) return;
        }
        Session.getInstance().clear();
        GuichetApplication.goTo("login.fxml");
    }

    // ================================================================
    //  Gestion d'erreur commune
    // ================================================================

    private void afficherErreur(Throwable e) {
        String message;
        if (e instanceof ApiException api) {
            message = api.getMessage();
        } else {
            message = e.getMessage() != null ? e.getMessage() : "Erreur inconnue.";
        }
        Ui.erreur("Erreur", message);
    }
}

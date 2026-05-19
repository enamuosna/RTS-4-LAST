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
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Session;
import sn.rts.caisse.guichet.util.Ui;
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

    /** Largeur minimale garantie pour la colonne Actions (jusqu'à 5 boutons). */
    private static final double LARGEUR_MIN_COLONNE_ACTIONS = 200.0;

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
    @FXML private TableColumn<OperationCaisseResponse, String> colClient;
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
    @FXML private FontIcon themeToggleIcon;

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
            themeToggleIcon.setIconLiteral(FontAwesomeSolid.SUN.getDescription());
            if (themeToggleButton != null) {
                themeToggleButton.setTooltip(new javafx.scene.control.Tooltip(
                        "Passer en mode clair"));
            }
        } else {
            // Mode clair actif → afficher une lune (clic = basculer en sombre)
            themeToggleIcon.setIconLiteral(FontAwesomeSolid.MOON.getDescription());
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
        colClient.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clientRaisonSociale));
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

            private final Button btnImprimer  = new Button();
            private final Button btnWhatsApp  = new Button();
            private final Button btnModifier  = new Button();
            private final Button btnReactiver = new Button();
            private final Button btnAnnuler   = new Button();
            private final HBox   box          = new HBox(4,
                    btnImprimer, btnWhatsApp, btnModifier, btnReactiver, btnAnnuler);

            {
                FontIcon iconPrint = new FontIcon(FontAwesomeSolid.PRINT);
                iconPrint.setIconSize(14);
                iconPrint.setIconColor(javafx.scene.paint.Color.web("#475569"));
                btnImprimer.setGraphic(iconPrint);
                btnImprimer.getStyleClass().addAll("button", "button-icon");
                btnImprimer.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnImprimer.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnImprimer.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnImprimer.setTooltip(new javafx.scene.control.Tooltip("Réimprimer le reçu"));

                FontIcon iconWa = new FontIcon(
                        org.kordamp.ikonli.fontawesome5.FontAwesomeBrands.WHATSAPP);
                iconWa.setIconSize(15);
                iconWa.setIconColor(javafx.scene.paint.Color.web("#25D366"));
                btnWhatsApp.setGraphic(iconWa);
                btnWhatsApp.getStyleClass().addAll("button", "button-icon");
                btnWhatsApp.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnWhatsApp.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnWhatsApp.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnWhatsApp.setTooltip(new javafx.scene.control.Tooltip(
                        "Envoyer le reçu par WhatsApp"));

                FontIcon iconModifier = new FontIcon(FontAwesomeSolid.PEN);
                iconModifier.setIconSize(13);
                iconModifier.setIconColor(javafx.scene.paint.Color.web("#1d4ed8"));
                btnModifier.setGraphic(iconModifier);
                btnModifier.getStyleClass().addAll("button", "button-icon");
                btnModifier.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnModifier.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnModifier.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnModifier.setTooltip(new javafx.scene.control.Tooltip(
                        "Corriger l'opération (agent de recette uniquement)"));

                FontIcon iconReactiver = new FontIcon(FontAwesomeSolid.UNDO);
                iconReactiver.setIconSize(13);
                iconReactiver.setIconColor(javafx.scene.paint.Color.web("#16a34a"));
                btnReactiver.setGraphic(iconReactiver);
                btnReactiver.getStyleClass().addAll("button", "button-icon");
                btnReactiver.setPrefSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnReactiver.setMinSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnReactiver.setMaxSize(TAILLE_BOUTON_ACTION, TAILLE_BOUTON_ACTION);
                btnReactiver.setTooltip(new javafx.scene.control.Tooltip(
                        "Réactiver l'opération annulée par erreur"));

                FontIcon iconCancel = new FontIcon(FontAwesomeSolid.TIMES_CIRCLE);
                iconCancel.setIconSize(14);
                iconCancel.setIconColor(javafx.scene.paint.Color.web("#dc2626"));
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
                btnModifier.setOnAction(e -> modifierOperation(op));
                btnReactiver.setOnAction(e -> reactiverOperation(op));
                btnAnnuler.setOnAction(e -> annulerOperation(op));

                btnWhatsApp.setDisable(false);
                btnWhatsApp.setOpacity(1.0);

                // Modifier et Réactiver : visibles uniquement pour AGENT_RECETTE
                // de cette caisse, SUPERVISEUR ou ADMIN. Cachés pour CAISSIER.
                boolean peutCorriger = peutCorriger();
                btnModifier.setVisible(peutCorriger && !op.annulee);
                btnModifier.setManaged(peutCorriger && !op.annulee);

                btnReactiver.setVisible(peutCorriger && op.annulee);
                btnReactiver.setManaged(peutCorriger && op.annulee);

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

        // Ouverture de caisse : CAISSIER affecte OU AGENT_RECETTE affecte.
        // Dans la realite metier RTS, l'agent de recette peut aussi tenir
        // la caisse pour la journee (il devient alors le detenteur).
        boolean peutOperer = peutFaireOperation(caisse);
        sn.rts.caisse.guichet.model.Dto.AuthResponse authDbg = Session.getInstance().getAuth();
        log.info("[ACCESS] role={} userId={} caisse={} caissierId={} agentRecetteId={} -> peutOperer={}",
                authDbg != null ? authDbg.role : null,
                authDbg != null ? authDbg.utilisateurId : null,
                caisse.code, caisse.caissierId, caisse.agentRecetteId, peutOperer);
        ouvrirButton.setVisible(!ouverte && !suspendue && peutOperer);
        ouvrirButton.setManaged(!ouverte && !suspendue && peutOperer);

        // Cloture : reservee a celui qui detient actuellement la caisse
        // (caissierId du DTO = utilisateur qui a ouvert la journee). Le
        // backend rejette deja toute autre tentative ("Seul le caissier qui
        // a ouvert la caisse peut la cloturer."). On masque le bouton ici
        // pour eviter le frottement UX.
        boolean estDetenteur = estDetenteurDeCetteCaisse(caisse);
        cloturerButton.setVisible(ouverte && estDetenteur);
        cloturerButton.setManaged(ouverte && estDetenteur);

        // Bouton "Nouvelle opération" : visible pour CAISSIER ou AGENT_RECETTE
        // affectes. Desactive tant que la caisse est fermee.
        nouvelleOperationButton.setVisible(peutOperer);
        nouvelleOperationButton.setManaged(peutOperer);
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
     * Charge les opérations de la <b>session de caisse en cours</b> depuis le
     * backend (journal pas encore clôturé), les <b>trie par date décroissante</b>
     * (la plus récente en premier) puis met à jour la liste maître. Les
     * listeners de FilteredList et de pagination répercutent automatiquement
     * le changement vers le tableau visible.
     *
     * <p>Dès que la caisse est clôturée, le backend renvoie une liste vide
     * (les opérations ont été rattachées au journal). Le tableau du guichet
     * se vide donc automatiquement ; l'historique complet reste accessible
     * côté admin via l'application web.</p>
     */
    private void chargerOperationsDuJour() {
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null) return;

        AsyncRunner.run(
                () -> api.operationsSessionCourante(caisse.id),
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

    /**
     * Renvoie true si l'utilisateur courant est le caissier affecté à
     * cette caisse (uniquement role CAISSIER).
     */
    private boolean estCaissierDeCetteCaisse(CaisseDTO caisse) {
        sn.rts.caisse.guichet.model.Dto.AuthResponse auth = Session.getInstance().getAuth();
        if (auth == null || caisse == null) return false;
        if (auth.role != sn.rts.caisse.guichet.model.Role.CAISSIER) return false;
        return caisse.caissierId != null && caisse.caissierId.equals(auth.utilisateurId);
    }

    /**
     * Renvoie true si l'utilisateur courant detient ACTUELLEMENT la caisse
     * (caissier_id du DTO est mis a jour a l'ouverture du journal et reflete
     * donc l'utilisateur qui a ouvert la journee, qu'il soit CAISSIER ou
     * AGENT_RECETTE). Permet de masquer le bouton "Cloturer" pour quiconque
     * n'a pas ouvert la caisse (le backend bloque deja, on evite la
     * frustration UX).
     */
    private boolean estDetenteurDeCetteCaisse(CaisseDTO caisse) {
        sn.rts.caisse.guichet.model.Dto.AuthResponse auth = Session.getInstance().getAuth();
        if (auth == null || caisse == null) return false;
        return caisse.caissierId != null && caisse.caissierId.equals(auth.utilisateurId);
    }

    /**
     * Renvoie true si l'utilisateur courant a le droit de modifier ou
     * réactiver les opérations de la caisse active :
     * <ul>
     *   <li>ADMIN, SUPERVISEUR : toujours</li>
     *   <li>AGENT_RECETTE : uniquement s'il est l'agent affecté à cette caisse</li>
     *   <li>CAISSIER : uniquement s'il est le caissier affecté à cette caisse
     *       (correction d'erreur de saisie sur sa propre journée). Le backend
     *       refusera de toute façon si la journée est cloturée ou si
     *       l'operation est deja annulee.</li>
     * </ul>
     */
    private boolean peutCorriger() {
        sn.rts.caisse.guichet.model.Dto.AuthResponse auth = Session.getInstance().getAuth();
        if (auth == null || auth.role == null) return false;
        sn.rts.caisse.guichet.model.Role role = auth.role;
        if (role == sn.rts.caisse.guichet.model.Role.ADMIN
                || role == sn.rts.caisse.guichet.model.Role.SUPERVISEUR) return true;
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null) return false;
        if (role == sn.rts.caisse.guichet.model.Role.AGENT_RECETTE) {
            return caisse.agentRecetteId != null
                    && caisse.agentRecetteId.equals(auth.utilisateurId);
        }
        if (role == sn.rts.caisse.guichet.model.Role.CAISSIER) {
            return caisse.caissierId != null
                    && caisse.caissierId.equals(auth.utilisateurId);
        }
        return false;
    }

    /**
     * Renvoie true si l'utilisateur courant a le droit de SAISIR une
     * nouvelle operation sur cette caisse :
     * <ul>
     *   <li>CAISSIER : uniquement le caissier affecte a cette caisse</li>
     *   <li>AGENT_RECETTE : uniquement l'agent de recette affecte a cette
     *       caisse. Dans la realite metier RTS, l'agent de recette peut
     *       aussi encaisser/decaisser, pas seulement corriger.</li>
     *   <li>ADMIN, SUPERVISEUR : non (ils utilisent l'app web pour le
     *       reporting, pas le guichet pour la saisie courante).</li>
     * </ul>
     */
    private boolean peutFaireOperation(CaisseDTO caisse) {
        sn.rts.caisse.guichet.model.Dto.AuthResponse auth = Session.getInstance().getAuth();
        if (auth == null || auth.role == null || caisse == null) return false;
        sn.rts.caisse.guichet.model.Role role = auth.role;
        if (role == sn.rts.caisse.guichet.model.Role.CAISSIER) {
            return caisse.caissierId != null && caisse.caissierId.equals(auth.utilisateurId);
        }
        if (role == sn.rts.caisse.guichet.model.Role.AGENT_RECETTE) {
            return caisse.agentRecetteId != null && caisse.agentRecetteId.equals(auth.utilisateurId);
        }
        return false;
    }

    private void reactiverOperation(OperationCaisseResponse op) {
        boolean ok = Ui.confirmer("Réactiver l'opération",
                "Réactiver l'opération " + op.numeroRecu + " ?\n\n"
                        + "Le montant sera ré-appliqué au solde de la caisse "
                        + "comme si l'annulation n'avait jamais eu lieu.");
        if (!ok) return;

        AsyncRunner.run(
                () -> api.reactiverOperation(op.id),
                reactivee -> {
                    Ui.info("Opération réactivée",
                            "Le reçu n° " + reactivee.numeroRecu
                                    + " est de nouveau actif. Le solde a été recalculé.");
                    rafraichirCaisse();
                },
                this::afficherErreur);
    }

    /**
     * Ouvre le modal « Nouvelle opération » en mode édition, pré-rempli avec
     * les valeurs de l'opération existante. Au clic Enregistrer, un PUT
     * /operations/{id} est envoyé. Le solde de la caisse est recalculé
     * automatiquement côté backend.
     */
    private void modifierOperation(OperationCaisseResponse op) {
        CaisseDTO caisse = Session.getInstance().getCaisseActive();
        if (caisse == null || caisse.statut != StatutCaisse.OUVERTE) {
            Ui.erreur("Caisse fermée",
                    "La caisse doit être ouverte pour modifier une opération.");
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
            modal.setTitle("Modifier l'opération " + op.numeroRecu);
            modal.setResizable(false);

            Scene scene = new Scene(root);
            ThemeManager.getInstance().register(scene);
            modal.setOnHidden(e -> ThemeManager.getInstance().unregister(scene));
            modal.setScene(scene);

            controller.initialiserPourModification(caisse, op, mise_a_jour -> {
                // Rafraîchir le tableau + le solde après modification
                rafraichirCaisse();
            });

            modal.showAndWait();

        } catch (Exception e) {
            log.error("Échec ouverture du modal Modification : {}", e.getMessage(), e);
            Ui.erreur("Erreur",
                    "Impossible d'ouvrir le formulaire de modification : " + e.getMessage());
        }
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

package sn.rts.caisse.guichet.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.ApiClient;
import sn.rts.caisse.guichet.api.ApiClient.PingResult;
import sn.rts.caisse.guichet.app.GuichetApplication;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Config;

import java.util.prefs.Preferences;

/**
 * Controleur de l'ecran "Connexion au serveur" (serveur.fxml).
 *
 * <h2>Responsabilites</h2>
 * <ul>
 *   <li>Saisie / validation / persistance de l'URL serveur</li>
 *   <li>Test de connectivite asynchrone via {@link ApiClient#ping()} (HTTP_1_1)</li>
 *   <li>Feedback visuel : pastille coloree, spinner, messages</li>
 *   <li>Bouton "Continuer" debloque uniquement apres un test reussi</li>
 *   <li>Case "Tester automatiquement au demarrage" memorisee entre sessions</li>
 * </ul>
 *
 * <h2>Mapping FXML attendu</h2>
 * <ul>
 *   <li>{@code urlField}          : TextField - URL du serveur</li>
 *   <li>{@code testerButton}      : Button    - "Tester la connexion"</li>
 *   <li>{@code testerButtonLabel} : Label     - texte du bouton "Tester" (optionnel)</li>
 *   <li>{@code continuerButton}   : Button    - "Continuer vers la connexion"</li>
 *   <li>{@code statusBox}         : HBox      - panneau resultat (status-success/error/...)</li>
 *   <li>{@code statusIcon}        : FontIcon  - pastille coloree</li>
 *   <li>{@code statusSpinner}     : ProgressIndicator - visible pendant le test</li>
 *   <li>{@code statusMessage}     : Label     - "Connecte" / "Connexion impossible" / ...</li>
 *   <li>{@code statusDetail}      : Label     - URL testee, message d'erreur</li>
 *   <li>{@code autoTestCheckBox}  : CheckBox  - "Tester automatiquement au demarrage"</li>
 * </ul>
 *
 * <h2>Actions FXML</h2>
 * <ul>
 *   <li>{@code #onTester}        : declenche un ping</li>
 *   <li>{@code #onReinitialiser} : remet l'URL par defaut + relance ping</li>
 *   <li>{@code #onContinuer}     : navigation vers l'ecran de login</li>
 * </ul>
 */
public class ServeurController {

    private static final Logger log = LoggerFactory.getLogger(ServeurController.class);

    /** Ecran a charger apres validation de la connexion. */
    private static final String LOGIN_FXML = "login.fxml";

    /** Cle pour memoriser la preference "auto-test" entre sessions. */
    private static final String PREF_AUTOTEST = "autoTest";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ServeurController.class);

    /** URL par defaut utilisee par le bouton de reinitialisation. */
    private static final String URL_PAR_DEFAUT = "http://localhost:9090/api";

    // ====================== FXML ======================
    @FXML private TextField         urlField;
    @FXML private Button            testerButton;
    @FXML private Label             testerButtonLabel;
    @FXML private Button            continuerButton;
    @FXML private HBox              statusBox;
    @FXML private FontIcon          statusIcon;
    @FXML private ProgressIndicator statusSpinner;
    @FXML private Label             statusMessage;
    @FXML private Label             statusDetail;
    @FXML private CheckBox          autoTestCheckBox;

    /** Vrai si la derniere tentative de connexion a reussi. */
    private boolean derniereConnexionOk = false;

    // ==================================================================
    //  Initialisation
    // ==================================================================

    @FXML
    public void initialize() {
        // 1. Pre-remplit le champ avec l'URL deja connue / configuree
        urlField.setText(Config.getInstance().getApiUrl());

        // 2. Charge la preference "auto-test"
        boolean autoTest = PREFS.getBoolean(PREF_AUTOTEST, true);
        if (autoTestCheckBox != null) {
            autoTestCheckBox.setSelected(autoTest);
            autoTestCheckBox.selectedProperty().addListener((obs, oldV, newV) ->
                    PREFS.putBoolean(PREF_AUTOTEST, newV));
        }

        // 3. Etat initial : neutre, bouton "Continuer" desactive
        showIdle();
        if (continuerButton != null) continuerButton.setDisable(true);

        // 4. Touche Entree dans le champ URL = declencher le test
        urlField.setOnAction(e -> onTester());

        // 5. Test automatique au demarrage si la case est cochee
        if (autoTest) {
            // Platform.runLater : attendre que la scene soit affichee
            Platform.runLater(this::onTester);
        }
    }

    // ==================================================================
    //  Actions FXML
    // ==================================================================

    /**
     * Action du bouton "Tester la connexion".
     * Valide l'URL puis lance un ping asynchrone vers /actuator/health.
     */
    @FXML
    public void onTester() {
        // Etape 1 : valider et persister l'URL avant le ping
        String saisi = urlField.getText();
        String normalise;
        try {
            normalise = Config.normalize(saisi);
        } catch (IllegalArgumentException ex) {
            showError("URL invalide", ex.getMessage());
            return;
        }
        urlField.setText(normalise);
        Config.getInstance().setApiUrl(normalise);

        // Etape 2 : ping asynchrone (UI non bloquee)
        showTesting();
        AsyncRunner.run(
                () -> ApiClient.getInstance().ping(),
                this::onPingResult,
                this::onPingError
        );
    }

    /**
     * Action du bouton de reinitialisation (icone refresh).
     * Restaure l'URL par defaut puis relance un test.
     */
    @FXML
    public void onReinitialiser() {
        urlField.setText(URL_PAR_DEFAUT);
        try {
            Config.getInstance().setApiUrl(URL_PAR_DEFAUT);
        } catch (IllegalArgumentException ignored) {
            // URL_PAR_DEFAUT est en dur, ne devrait jamais echouer
        }
        onTester();
    }

    /**
     * Action du bouton "Continuer vers la connexion".
     * Charge l'ecran de login si la derniere tentative a reussi.
     */
    @FXML
    public void onContinuer() {
        if (!derniereConnexionOk) {
            showError("Connexion non vérifiée",
                    "Cliquez d'abord sur « Tester la connexion ».");
            return;
        }
        try {
            GuichetApplication.goTo(LOGIN_FXML);
        } catch (Exception e) {
            log.error("Impossible de naviguer vers {}", LOGIN_FXML, e);
            showError("Navigation impossible",
                    "Impossible de charger l'écran de connexion : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Callbacks ping
    // ==================================================================

    private void onPingResult(PingResult result) {
        if (result.ok()) {
            log.info("Ping OK : {}", Config.getInstance().getServerBaseUrl());
            showSuccess("Connecté", result.message());
            derniereConnexionOk = true;
        } else {
            log.warn("Ping KO : {}", result.message());
            showError("Connexion impossible", result.message());
            derniereConnexionOk = false;
        }
    }

    private void onPingError(Throwable t) {
        log.error("Erreur inattendue durant le ping", t);
        showError("Erreur inattendue",
                t.getMessage() == null ? t.toString() : t.getMessage());
        derniereConnexionOk = false;
    }

    // ==================================================================
    //  Etats visuels
    // ==================================================================

    private void showIdle() {
        applyStatus("status-idle",
                "Non testé",
                "Cliquez sur « Tester la connexion » pour vérifier.");
        showSpinner(false);
        setControlsDisabled(false);
        if (continuerButton != null) continuerButton.setDisable(true);
    }

    private void showTesting() {
        applyStatus("status-testing",
                "Test en cours…",
                "Vérification de " + Config.getInstance().getServerBaseUrl());
        showSpinner(true);
        setControlsDisabled(true);
        if (continuerButton != null) continuerButton.setDisable(true);
    }

    private void showSuccess(String title, String detail) {
        applyStatus("status-success", title, detail);
        showSpinner(false);
        setControlsDisabled(false);
        if (continuerButton != null) continuerButton.setDisable(false);
    }

    private void showError(String title, String message) {
        applyStatus("status-error", title, message);
        showSpinner(false);
        setControlsDisabled(false);
        if (continuerButton != null) continuerButton.setDisable(true);
    }

    /**
     * Met a jour la classe CSS du panneau de statut + les libelles.
     * Les classes possibles ({@code status-idle/testing/success/error})
     * sont definies dans le CSS de l'application.
     */
    private void applyStatus(String stateClass, String title, String detail) {
        if (statusBox != null) {
            statusBox.getStyleClass().removeAll(
                    "status-idle", "status-testing", "status-success", "status-error");
            statusBox.getStyleClass().add(stateClass);
        }
        if (statusIcon != null) {
            statusIcon.getStyleClass().removeAll(
                    "status-icon-idle", "status-icon-testing",
                    "status-icon-success", "status-icon-error");
            statusIcon.getStyleClass().add(stateClass.replace("status-", "status-icon-"));
        }
        if (statusMessage != null) statusMessage.setText(title);
        if (statusDetail  != null) statusDetail.setText(detail);
    }

    /**
     * Affiche/masque le spinner ET inversement l'icone de statut
     * (un seul des deux est visible a la fois).
     */
    private void showSpinner(boolean spinning) {
        if (statusSpinner != null) {
            statusSpinner.setVisible(spinning);
            statusSpinner.setManaged(spinning);
        }
        if (statusIcon != null) {
            statusIcon.setVisible(!spinning);
            statusIcon.setManaged(!spinning);
        }
    }

    private void setControlsDisabled(boolean disabled) {
        if (testerButton != null) testerButton.setDisable(disabled);
        if (urlField    != null)  urlField.setDisable(disabled);
    }
}

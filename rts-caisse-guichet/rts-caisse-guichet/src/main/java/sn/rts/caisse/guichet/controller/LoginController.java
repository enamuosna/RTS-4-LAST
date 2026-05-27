package sn.rts.caisse.guichet.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import sn.rts.caisse.guichet.api.ApiException;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.app.GuichetApplication;
import sn.rts.caisse.guichet.model.Dto.AuthResponse;
import sn.rts.caisse.guichet.model.Role;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Session;
import sn.rts.caisse.guichet.util.Ui;

/**
 * Contrôleur de l'écran de connexion.
 * Authentifie via {@code /api/auth/login}, stocke la session en mémoire
 * et redirige vers l'écran principal du caissier.
 *
 * <p>Version modernisée :
 * <ul>
 *   <li>Le label d'erreur n'est rendu visible que lorsqu'il y a un message
 *       (banner rouge), et masqué sinon.</li>
 *   <li>Le bouton expose un Label séparé ({@code loginButtonLabel}) pour
 *       changer le texte sans détruire l'icône SVG du graphic.</li>
 *   <li>La navigation utilise {@link GuichetApplication#goTo(String)} qui
 *       applique automatiquement une transition fluide.</li>
 * </ul>
 */
public class LoginController {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label loginButtonLabel;
    @FXML private Label errorLabel;

    private final CaisseApi api = CaisseApi.getInstance();

    @FXML
    public void initialize() {
        hideError();
        // Synchronise le focus des champs avec leur HBox parent stylé
        wireFieldFocusToParent(loginField);
        wireFieldFocusToParent(passwordField);
        // Focus initial sur le champ identifiant pour une saisie immédiate
        loginField.requestFocus();
    }

    /**
     * Ajoute la classe CSS "field-focused" au parent du champ quand celui-ci
     * a le focus, ce qui permet d'afficher le focus ring sur le HBox stylé
     * (« text-field-with-icon ») englobant l'icône et le champ.
     */
    private static void wireFieldFocusToParent(javafx.scene.Node field) {
        field.focusedProperty().addListener((obs, was, isFocused) -> {
            javafx.scene.Parent parent = field.getParent();
            if (parent == null) return;
            if (isFocused) {
                if (!parent.getStyleClass().contains("field-focused")) {
                    parent.getStyleClass().add("field-focused");
                }
            } else {
                parent.getStyleClass().remove("field-focused");
            }
        });
    }

    @FXML
    public void onLogin() {
        String login = loginField.getText() == null ? "" : loginField.getText().trim();
        String password = passwordField.getText();

        if (login.isEmpty() || password == null || password.isEmpty()) {
            showError("Identifiant et mot de passe requis.");
            return;
        }

        setEnabled(false);
        hideError();

        AsyncRunner.run(
                () -> api.login(login, password),
                this::onLoginSuccess,
                this::onLoginError);
    }

    private void onLoginSuccess(AuthResponse response) {
        Session.getInstance().setAuth(response);

        // Le guichet JavaFX est ouvert :
        //   - CAISSIER       : saisie d'operations sur sa caisse
        //   - AGENT_RECETTE  : controle + saisie sur sa caisse affectee
        //   - SUPERVISEUR    : supervision + modification/annulation/reactivation
        //                      des operations sur TOUTES les caisses
        // L'ADMIN utilise uniquement l'app web pour l'administration globale.
        if (response.role != Role.CAISSIER
                && response.role != Role.AGENT_RECETTE
                && response.role != Role.SUPERVISEUR) {
            Session.getInstance().clear();
            setEnabled(true);
            Ui.erreur("Accès au guichet refusé",
                    "Le client guichet est réservé aux caissiers, agents de recette "
                            + "et superviseurs.\n\n"
                            + "Les administrateurs utilisent uniquement l'application "
                            + "web pour la gestion du systeme.");
            return;
        }

        GuichetApplication.goTo("caissier.fxml");
    }

    private void onLoginError(Throwable err) {
        setEnabled(true);
        String message;
        if (err instanceof ApiException api) {
            message = switch (api.getStatus()) {
                case 401 -> "Identifiants invalides.";
                case 0   -> "Serveur RTS injoignable. Vérifiez votre connexion.";
                default  -> api.getMessage();
            };
        } else {
            message = err.getMessage() != null ? err.getMessage() : "Erreur de connexion.";
        }
        showError(message);
    }

    private void setEnabled(boolean enabled) {
        loginButton.setDisable(!enabled);
        loginField.setDisable(!enabled);
        passwordField.setDisable(!enabled);
        if (loginButtonLabel != null) {
            loginButtonLabel.setText(enabled ? "Se connecter" : "Connexion en cours...");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @FXML
    public void onChangerServeur() {
        GuichetApplication.goTo("serveur.fxml");
    }
}

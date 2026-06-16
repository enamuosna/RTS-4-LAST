package sn.rts.caisse.guichet.util;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * Boîte de dialogue personnalisée RTS.
 *
 * <p>Remplace les {@link javafx.scene.control.Alert} natifs de JavaFX (qui
 * affichent une fenêtre blanche avec la barre de titre Windows et l'icône Java
 * par défaut) par une fenêtre <b>sans décoration</b>, transparente, aux coins
 * arrondis, avec une ombre portée et un en-tête coloré selon la sémantique du
 * message (information, succès, avertissement, erreur, confirmation).</p>
 *
 * <h2>Caractéristiques</h2>
 * <ul>
 *   <li>Fenêtre {@link StageStyle#TRANSPARENT} → habillage 100 % CSS, cohérent
 *       avec le thème clair/sombre courant ({@link ThemeManager}).</li>
 *   <li>Modale ({@link Modality#APPLICATION_MODAL}) et centrée sur la fenêtre
 *       active.</li>
 *   <li>Déplaçable en saisissant l'en-tête.</li>
 *   <li>{@code Entrée} valide le bouton par défaut, {@code Échap} annule.</li>
 *   <li>Léger fondu d'apparition.</li>
 * </ul>
 *
 * <h2>Utilisation</h2>
 * <pre>
 *   // Message simple
 *   RtsDialog.message(RtsDialog.Type.SUCCESS, "Versement", "Opération enregistrée.");
 *
 *   // Confirmation
 *   boolean ok = RtsDialog.confirm("Caisse ouverte", "Se déconnecter quand même ?");
 *
 *   // Saisie
 *   Optional&lt;String&gt; m = RtsDialog.prompt("Ouverture", "Fond de caisse ?", "0");
 *
 *   // Boutons personnalisés
 *   Optional&lt;String&gt; choix = RtsDialog.&lt;String&gt;create()
 *           .type(Type.SUCCESS).title("Reçu").message("Que faire ?")
 *           .defaultButton("Imprimer", ButtonKind.PRIMARY, "print")
 *           .button("WhatsApp", ButtonKind.SUCCESS, "whatsapp")
 *           .cancelButton("Fermer", ButtonKind.SECONDARY, null)
 *           .showAndWait();
 * </pre>
 *
 * @param <T> type de la valeur renvoyée par le bouton cliqué
 */
public final class RtsDialog<T> {

    // ──────────────────────────────────────────────────────────────────────
    //  Sémantique : couleur d'en-tête + glyphe du badge
    // ──────────────────────────────────────────────────────────────────────
    public enum Type {
        INFO("type-info", "i"),
        SUCCESS("type-success", "✓"),   // ✓
        WARNING("type-warning", "!"),
        ERROR("type-error", "✕"),       // ✕
        CONFIRM("type-confirm", "?");

        final String styleClass;
        final String glyph;
        Type(String styleClass, String glyph) {
            this.styleClass = styleClass;
            this.glyph = glyph;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Apparence des boutons (réutilise les classes .button-* du thème)
    // ──────────────────────────────────────────────────────────────────────
    public enum ButtonKind {
        PRIMARY("button-primary"),
        SECONDARY("button-secondary"),
        DANGER("button-danger"),
        SUCCESS("button-success"),
        GHOST("button-ghost");

        final String styleClass;
        ButtonKind(String styleClass) { this.styleClass = styleClass; }
    }

    private record Btn<T>(String label, ButtonKind kind, T value,
                          boolean returnsInput, boolean isDefault, boolean isCancel) {}

    // ──────────────────────────────────────────────────────────────────────
    //  État du constructeur fluide
    // ──────────────────────────────────────────────────────────────────────
    private Type type = Type.INFO;
    private String title = "";
    private String message = "";
    private double width = 440;
    private boolean monospace = false;
    private boolean withInput = false;
    private String inputDefault = "";
    private T cancelValue = null;
    private final List<Btn<T>> buttons = new ArrayList<>();

    private RtsDialog() {}

    public static <T> RtsDialog<T> create() { return new RtsDialog<>(); }

    // ──────────────────────────────────────────────────────────────────────
    //  API fluide
    // ──────────────────────────────────────────────────────────────────────
    public RtsDialog<T> type(Type t)            { this.type = t; return this; }
    public RtsDialog<T> title(String t)         { this.title = t; return this; }
    public RtsDialog<T> message(String m)       { this.message = m; return this; }
    public RtsDialog<T> width(double w)         { this.width = w; return this; }
    public RtsDialog<T> monospace()             { this.monospace = true; return this; }
    public RtsDialog<T> input(String def)       { this.withInput = true; this.inputDefault = def; return this; }
    public RtsDialog<T> cancelValue(T v)        { this.cancelValue = v; return this; }

    /** Bouton standard. */
    public RtsDialog<T> button(String label, ButtonKind kind, T value) {
        buttons.add(new Btn<>(label, kind, value, false, false, false));
        return this;
    }

    /** Bouton par défaut (déclenché par {@code Entrée}). */
    public RtsDialog<T> defaultButton(String label, ButtonKind kind, T value) {
        buttons.add(new Btn<>(label, kind, value, false, true, false));
        return this;
    }

    /** Bouton d'annulation (déclenché par {@code Échap}). */
    public RtsDialog<T> cancelButton(String label, ButtonKind kind, T value) {
        buttons.add(new Btn<>(label, kind, value, false, false, true));
        return this;
    }

    /** Bouton par défaut qui renvoie le texte saisi dans le champ de saisie. */
    public RtsDialog<T> inputButton(String label, ButtonKind kind) {
        buttons.add(new Btn<>(label, kind, null, true, true, false));
        return this;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Affichage bloquant
    // ──────────────────────────────────────────────────────────────────────
    public Optional<T> showAndWait() {
        if (Platform.isFxApplicationThread()) {
            return Optional.ofNullable(doShow());
        }
        // Appel hors thread FX : on bascule sur le thread JavaFX et on bloque
        // l'appelant jusqu'à la fermeture de la boîte.
        final Object[] box = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { box[0] = doShow(); }
            finally { latch.countDown(); }
        });
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        @SuppressWarnings("unchecked")
        T v = (T) box[0];
        return Optional.ofNullable(v);
    }

    @SuppressWarnings("unchecked")
    private T doShow() {
        final Object[] result = { cancelValue };

        Stage stage = new Stage();
        Window owner = activeWindow();
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        // ----- En-tête : badge + titre + bouton fermer -----
        Label badge = new Label(type.glyph);
        badge.getStyleClass().add("rts-dialog-badge");

        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("rts-dialog-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("rts-dialog-close");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> { result[0] = cancelValue; stage.close(); });

        HBox header = new HBox(14, badge, titleLabel, closeBtn);
        header.getStyleClass().add("rts-dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Déplacement de la fenêtre en saisissant l'en-tête
        final double[] drag = new double[2];
        header.setOnMousePressed(ev -> {
            drag[0] = ev.getScreenX() - stage.getX();
            drag[1] = ev.getScreenY() - stage.getY();
        });
        header.setOnMouseDragged(ev -> {
            stage.setX(ev.getScreenX() - drag[0]);
            stage.setY(ev.getScreenY() - drag[1]);
        });

        // ----- Corps : message (+ champ de saisie optionnel) -----
        VBox body = new VBox(14);
        body.getStyleClass().add("rts-dialog-body");

        Label msg = new Label(message == null ? "" : message);
        msg.getStyleClass().add("rts-dialog-message");
        if (monospace) msg.getStyleClass().add("rts-dialog-message-mono");
        msg.setWrapText(true);
        msg.setMaxWidth(width - 48);
        body.getChildren().add(msg);

        final TextField field = withInput
                ? new TextField(inputDefault == null ? "" : inputDefault)
                : null;
        if (field != null) {
            field.getStyleClass().add("rts-dialog-input");
            body.getChildren().add(field);
        }

        // ----- Pied : boutons -----
        HBox footer = new HBox(10);
        footer.getStyleClass().add("rts-dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        boolean single = buttons.size() == 1;
        for (Btn<T> b : buttons) {
            Button button = new Button(b.label());
            button.getStyleClass().addAll("button", b.kind().styleClass);
            // Un bouton unique répond à la fois à Entrée et Échap.
            if (b.isDefault() || single) button.setDefaultButton(true);
            if (b.isCancel() || single)  button.setCancelButton(true);
            button.setOnAction(e -> {
                result[0] = (b.returnsInput() && field != null) ? field.getText() : b.value();
                stage.close();
            });
            footer.getChildren().add(button);
        }

        // ----- Assemblage -----
        VBox card = new VBox(header, body, footer);
        card.getStyleClass().addAll("rts-dialog", type.styleClass);
        card.setMaxWidth(width);
        card.setPrefWidth(width);

        // Marge transparente autour de la carte pour laisser respirer l'ombre.
        StackPane root = new StackPane(card);
        root.getStyleClass().add("rts-dialog-overlay");
        root.setPadding(new Insets(30));
        root.setOpacity(0); // fondu d'apparition piloté dans setOnShown

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Applique le CSS du thème courant (clair/sombre) à la boîte.
        ThemeManager.getInstance().register(scene);
        stage.setOnHidden(e -> ThemeManager.getInstance().unregister(scene));

        stage.setScene(scene);
        stage.sizeToScene();

        stage.setOnShown(ev -> {
            centerOnOwner(stage, owner);
            if (field != null) { field.requestFocus(); field.selectAll(); }
            FadeTransition fade = new FadeTransition(Duration.millis(150), root);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });

        stage.showAndWait();
        return (T) result[0];
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Raccourcis statiques (équivalents des anciens Alert)
    // ──────────────────────────────────────────────────────────────────────

    /** Message simple à un seul bouton « OK ». */
    public static void message(Type type, String title, String message) {
        RtsDialog.<Void>create()
                .type(type).title(title).message(message)
                .cancelValue(null)
                .button("OK", ButtonKind.PRIMARY, null)
                .showAndWait();
    }

    /** Confirmation Oui/Non. Renvoie {@code true} si l'utilisateur confirme. */
    public static boolean confirm(String title, String message) {
        return confirm(title, message, "Confirmer", "Annuler");
    }

    public static boolean confirm(String title, String message,
                                  String okLabel, String cancelLabel) {
        return RtsDialog.<Boolean>create()
                .type(Type.CONFIRM).title(title).message(message)
                .cancelValue(false)
                .defaultButton(okLabel, ButtonKind.PRIMARY, true)
                .cancelButton(cancelLabel, ButtonKind.SECONDARY, false)
                .showAndWait()
                .orElse(false);
    }

    /** Saisie de texte. Renvoie {@link Optional#empty()} si annulé. */
    public static Optional<String> prompt(String title, String message, String defaut) {
        return RtsDialog.<String>create()
                .type(Type.CONFIRM).title(title).message(message)
                .input(defaut == null ? "" : defaut)
                .cancelValue(null)
                .inputButton("Valider", ButtonKind.PRIMARY)
                .cancelButton("Annuler", ButtonKind.SECONDARY, null)
                .showAndWait();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Helpers internes
    // ──────────────────────────────────────────────────────────────────────

    /** Fenêtre actuellement active (pour ancrer/ centrer la boîte modale). */
    private static Window activeWindow() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w.isFocused()) return w;
        }
        Stage primary = sn.rts.caisse.guichet.app.GuichetApplication.getPrimaryStage();
        if (primary != null && primary.isShowing()) return primary;
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) return w;
        }
        return null;
    }

    private static void centerOnOwner(Stage stage, Window owner) {
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (owner != null && owner.getWidth() > 0 && !Double.isNaN(w)) {
            stage.setX(owner.getX() + (owner.getWidth() - w) / 2.0);
            stage.setY(owner.getY() + (owner.getHeight() - h) / 2.0);
        } else {
            stage.centerOnScreen();
        }
    }
}

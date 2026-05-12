    package sn.rts.caisse.guichet.app;

    import javafx.animation.FadeTransition;
    import javafx.animation.Interpolator;
    import javafx.animation.ParallelTransition;
    import javafx.animation.TranslateTransition;
    import javafx.application.Application;
    import javafx.fxml.FXMLLoader;
    import javafx.scene.Parent;
    import javafx.scene.Scene;
    import javafx.scene.image.Image;
    import javafx.scene.layout.StackPane;
    import javafx.stage.Stage;
    import javafx.util.Duration;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import sn.rts.caisse.guichet.util.AsyncRunner;
    import sn.rts.caisse.guichet.util.Config;
    import sn.rts.caisse.guichet.util.ThemeManager;

    import java.io.IOException;
    import java.net.URL;

    /**
     * Classe principale JavaFX : démarre l'application et expose une méthode
     * statique {@link #goTo(String)} pour la navigation entre écrans.
     *
     * <p>La navigation utilise une <b>scène persistante</b> dont la racine est un
     * {@link StackPane} : à chaque appel à {@code goTo}, l'ancien écran s'efface
     * en fondu pendant que le nouveau apparaît, ce qui produit une transition
     * fluide sans flash blanc (problème de l'ancienne approche
     * {@code setScene(new Scene(...))}).</p>
     */
    public class GuichetApplication extends Application {

        private static final Logger log = LoggerFactory.getLogger(GuichetApplication.class);

        /** Durées des animations de transition entre écrans. */
        private static final Duration FADE_OUT_DURATION = Duration.millis(180);
        private static final Duration FADE_IN_DURATION  = Duration.millis(280);
        private static final double   SLIDE_OFFSET_PX   = 14;

        private static Stage primaryStage;
        private static StackPane rootContainer;
        private static Scene scene;

        public static void main(String[] args) {
            launch(args);
        }

        @Override
        public void start(Stage stage) {
            primaryStage = stage;
            stage.setTitle(Config.APP_TITLE);

            // --- Conteneur racine persistant ----------------------------------
            rootContainer = new StackPane();
            // rootContainer.setStyle("-fx-background-color: #f1f5f9;"); // matche -rts-gray-100

            scene = new Scene(rootContainer, 1200, 760);

            //URL css = GuichetApplication.class.getResource("/css/style.css");
            // if (css != null) {
            //scene.getStylesheets().add(css.toExternalForm());
            //}
            ThemeManager.getInstance().register(scene);
            // Icône fenêtre (si présente dans les ressources)
            URL iconUrl = GuichetApplication.class.getResource("/images/app-icon.png");
            if (iconUrl != null) {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }

            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(640);

            // Premier écran (sans transition d'entrée puisqu'aucun précédent)
            loadInitialScreen("serveur.fxml");

            stage.show();

            log.info("Guichet RTS démarré. API cible : {}", Config.getInstance().getApiUrl());
        }

        /**
         * Charge un FXML depuis {@code /fxml/} et l'affiche comme contenu courant.
         * Une transition de fondu est automatiquement appliquée entre l'écran
         * précédent et le nouveau pour une UX plus fluide.
         */
        public static void goTo(String fxmlName) {
            try {
                Parent newRoot = loadFxml(fxmlName);

                if (rootContainer.getChildren().isEmpty()) {
                    // Cas du tout premier appel : pas d'animation de sortie
                    rootContainer.getChildren().add(newRoot);
                    playEntranceAnimation(newRoot);
                    return;
                }

                // Transition fade-out / fade-in entre l'ancien et le nouveau
                Parent oldRoot = (Parent) rootContainer.getChildren().get(0);

                FadeTransition fadeOut = new FadeTransition(FADE_OUT_DURATION, oldRoot);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setInterpolator(Interpolator.EASE_BOTH);

                fadeOut.setOnFinished(e -> {
                    rootContainer.getChildren().setAll(newRoot);
                    playEntranceAnimation(newRoot);
                });

                fadeOut.play();

            } catch (IOException e) {
                log.error("Impossible de charger {}", fxmlName, e);
                throw new RuntimeException("Erreur de navigation : " + e.getMessage(), e);
            }
        }

        /**
         * Charge directement le premier écran sans transition de sortie.
         */
        private static void loadInitialScreen(String fxmlName) {
            try {
                Parent root = loadFxml(fxmlName);
                rootContainer.getChildren().add(root);
                playEntranceAnimation(root);
            } catch (IOException e) {
                log.error("Impossible de charger l'écran initial {}", fxmlName, e);
                throw new RuntimeException("Erreur démarrage : " + e.getMessage(), e);
            }
        }

        /**
         * Joue une animation d'entrée (fondu + léger glissement vers le haut)
         * sur le node fourni. Utilisée à chaque changement d'écran et au démarrage.
         */
        private static void playEntranceAnimation(Parent node) {
            node.setOpacity(0.0);
            node.setTranslateY(SLIDE_OFFSET_PX);

            FadeTransition fadeIn = new FadeTransition(FADE_IN_DURATION, node);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slideUp = new TranslateTransition(FADE_IN_DURATION, node);
            slideUp.setFromY(SLIDE_OFFSET_PX);
            slideUp.setToY(0);
            slideUp.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(fadeIn, slideUp).play();
        }

        /**
         * Charge un FXML depuis le classpath (sans le poser dans la scène).
         */
        private static Parent loadFxml(String fxmlName) throws IOException {
            URL fxml = GuichetApplication.class.getResource("/fxml/" + fxmlName);
            if (fxml == null) {
                throw new IOException("FXML introuvable : " + fxmlName);
            }
            return new FXMLLoader(fxml).load();
        }

        public static Stage getPrimaryStage() {
            return primaryStage;
        }

        @Override
        public void stop() {
            AsyncRunner.shutdown();
        }
    }

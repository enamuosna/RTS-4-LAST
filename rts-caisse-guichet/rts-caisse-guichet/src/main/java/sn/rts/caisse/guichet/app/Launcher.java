    package sn.rts.caisse.guichet.app;

    import javafx.application.Application;

    /**
     * Point d'entrée JVM du client guichet.
     * <p>
     * Cette classe existe uniquement pour contourner la vérification stricte
     * du JDK 11+ : quand la classe {@code main} étend directement
     * {@link javafx.application.Application}, le lanceur Java exige la
     * présence explicite des modules JavaFX dans le {@code --module-path},
     * et affiche sinon le message "composants d'exécution JavaFX manquants".
     * <p>
     * En passant par une classe qui <b>n'étend pas</b> {@code Application},
     * on évite cette vérification : le runtime JavaFX est chargé
     * dynamiquement depuis le classpath Maven, ce qui fonctionne aussi bien
     * depuis IntelliJ que depuis {@code mvn javafx:run} ou un JAR packagé.
     */
    public class Launcher {

        public static void main(String[] args) {
            Application.launch(GuichetApplication.class, args);
        }
    }

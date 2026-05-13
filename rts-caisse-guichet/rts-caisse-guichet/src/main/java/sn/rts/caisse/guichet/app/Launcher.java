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
            // Force la pile IPv4 pour les connexions sortantes Java.
            // Sur de nombreux postes Windows derrière un FAI africain,
            // la résolution AAAA renvoie une adresse IPv6 routable
            // localement mais bloquée en sortie : la JVM y bute pendant
            // 20–30s avant de tomber sur IPv4. Ce flag fait pointer le
            // resolver et la pile socket directement sur IPv4.
            //
            // Doit être défini AVANT tout chargement de classe réseau
            // (HttpURLConnection, HttpClient, Socket…). On est dans le
            // tout premier instruction du main, c'est OK.
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv4Addresses", "true");

            Application.launch(GuichetApplication.class, args);
        }
    }

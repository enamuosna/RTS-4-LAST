package sn.rts.caisse.guichet.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration applicative : URL backend et constantes.
 * <p>
 * Ordre de priorité pour l'URL :
 *   1. Propriété système {@code -Drts.api.url=...}
 *   2. Variable d'environnement {@code RTS_API_URL}
 *   3. Fichier persistant {@code ~/.rts-caisse/config.properties}
 *   4. Valeur par défaut {@code http://localhost:9090/api}
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static final String DEFAULT_API_URL = "http://localhost:8090/api";
    private static final String SYSTEM_PROP_KEY = "rts.api.url";
    private static final String ENV_VAR_KEY     = "RTS_API_URL";
    private static final String FILE_KEY        = "api.url";

    private static final Path CONFIG_DIR  = Paths.get(System.getProperty("user.home"), ".rts-caisse");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    public static final String APP_TITLE   = "RTS Caisse — Guichet";
    public static final String APP_VERSION = "1.0.0";

    // ============== Singleton ==============
    private static final Config INSTANCE = new Config();
    public static Config getInstance() { return INSTANCE; }

    private volatile String apiUrl;

    private Config() {
        this.apiUrl = resolveInitialUrl();
        log.info("URL API initiale : {}", apiUrl);
    }

    // ============== API publique ==============

    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * Met à jour l'URL en mémoire ET la persiste dans le fichier de config.
     * Les appels API suivants utiliseront immédiatement la nouvelle valeur.
     *
     * @throws IllegalArgumentException si l'URL est vide ou ne commence pas par http(s)://
     */
    public void setApiUrl(String url) {
        String normalized = normalize(url);
        if (normalized.equals(this.apiUrl)) {
            return;
        }
        this.apiUrl = normalized;
        persist(normalized);
        log.info("URL API mise à jour : {}", normalized);
    }

    /**
     * Dérive l'URL de base du serveur (sans le suffixe /api) à partir de l'URL API.
     * Utile pour atteindre des endpoints hors /api comme /actuator/health.
     *
     *   http://server:9090/api   →  http://server:9090
     *   http://server:9090/api/  →  http://server:9090
     *   http://server:9090       →  http://server:9090
     */
    public String getServerBaseUrl() {
        return apiUrl.replaceFirst("/api/?$", "");
    }

    // ============== Validation / normalisation ==============

    /**
     * Normalise une URL saisie par l'utilisateur :
     *   - trim
     *   - retire le slash final
     *   - ajoute /api si l'utilisateur a saisi juste http://server:9090
     *   - vérifie le préfixe http(s)://
     */
    public static String normalize(String url) {
        if (url == null) {
            throw new IllegalArgumentException("URL vide.");
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("URL vide.");
        }
        if (!trimmed.matches("^https?://.+")) {
            throw new IllegalArgumentException(
                "URL invalide. Format attendu : http://host:port ou http://host:port/api");
        }
        // retire le / final
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // ajoute /api si absent
        if (!trimmed.endsWith("/api")) {
            trimmed = trimmed + "/api";
        }
        return trimmed;
    }

    // ============== Initialisation / persistance ==============

    private static String resolveInitialUrl() {
        // 1. -D system property
        String fromSystem = System.getProperty(SYSTEM_PROP_KEY);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return safeNormalize(fromSystem, "système");
        }
        // 2. variable d'environnement
        String fromEnv = System.getenv(ENV_VAR_KEY);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return safeNormalize(fromEnv, "environnement");
        }
        // 3. fichier de config
        String fromFile = readFromFile();
        if (fromFile != null && !fromFile.isBlank()) {
            return safeNormalize(fromFile, "fichier");
        }
        // 4. valeur par défaut
        return DEFAULT_API_URL;
    }

    private static String safeNormalize(String url, String source) {
        try {
            return normalize(url);
        } catch (IllegalArgumentException e) {
            log.warn("URL invalide depuis {} ({}), utilisation du défaut.", source, e.getMessage());
            return DEFAULT_API_URL;
        }
    }

    private static String readFromFile() {
        if (!Files.exists(CONFIG_FILE)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
            return props.getProperty(FILE_KEY);
        } catch (IOException e) {
            log.warn("Lecture impossible du fichier de config {} : {}", CONFIG_FILE, e.getMessage());
            return null;
        }
    }

    private static void persist(String url) {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            Properties props = new Properties();
            // Recharger pour ne pas écraser d'éventuelles autres clés futures
            if (Files.exists(CONFIG_FILE)) {
                try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                    props.load(in);
                }
            }
            props.setProperty(FILE_KEY, url);
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "RTS Caisse Guichet — configuration locale");
            }
        } catch (IOException e) {
            log.error("Persistance impossible vers {} : {}", CONFIG_FILE, e.getMessage());
        }
    }
}

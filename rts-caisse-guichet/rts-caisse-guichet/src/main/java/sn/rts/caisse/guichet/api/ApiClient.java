package sn.rts.caisse.guichet.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Config;
import sn.rts.caisse.guichet.util.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import sn.rts.caisse.guichet.model.Dto.ClientCreateRequest;
import sn.rts.caisse.guichet.model.Dto.ClientDTO;

/**
 * Client HTTP centralise pour le guichet RTS.
 *
 * <h2>Strategie de ping en cascade</h2>
 * Pour le test de connectivite, {@link #ping()} essaie 3 strategies dans
 * l'ordre, jusqu'a ce qu'une reussisse :
 * <ol>
 *   <li><b>HttpURLConnection</b> (Java natif, HTTP/1.1 strict)</li>
 *   <li><b>curl.exe</b> en sous-processus (Windows 10+, fallback infaillible)</li>
 * </ol>
 * Cette redondance protege contre tout probleme de proxy, HTTP/2 ou
 * configuration JVM specifique a un poste.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private static final ApiClient INSTANCE = new ApiClient();
    public static ApiClient getInstance() { return INSTANCE; }


    /**
     * Crée un nouveau client en base via POST /api/clients.
     * Le serveur applique sa propre validation (Bean Validation) et
     * retourne le ClientDTO complet avec l'id généré.
     *
     * @param requete payload validé localement (raisonSociale non-vide)
     * @return le client persisté, avec son id
     * @throws ApiException si le serveur refuse (400, 409, etc.) ou
     *                      si le réseau échoue
     */
    /** Client HTTP moderne pour les appels metier. HTTP/1.1 force.
     *  connectTimeout généreux (30s) car les premières poignées de main TLS
     *  Let's Encrypt sont parfois lentes, surtout quand plusieurs appels
     *  partent en parallèle au chargement d'un écran. */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Nombre maximum de tentatives en cas de timeout de connexion. */
    private static final int MAX_RETRIES_ON_CONNECT_TIMEOUT = 2;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ==================================================================
    //  Ping de connectivite
    // ==================================================================

    public record PingResult(boolean ok, String message) {
        public static PingResult success() {
            return new PingResult(true, "Le serveur répond correctement.");
        }
        public static PingResult failure(String message) {
            return new PingResult(false, message);
        }
    }

    /**
     * Teste la disponibilite du serveur via {@code GET /actuator/health}.
     * Strategie en cascade : HttpURLConnection -> curl.exe.
     */
    public PingResult ping() {
        String url = Config.getInstance().getServerBaseUrl() + "/actuator/health";

        // Strategie 1 : HttpURLConnection natif Java
        PingResult r1 = pingViaHttpUrlConnection(url);
        if (r1.ok()) {
            log.debug("Ping OK via HttpURLConnection");
            return r1;
        }
        log.debug("Ping HttpURLConnection : {}", r1.message());

        // Strategie 2 : curl.exe en sous-processus (fallback infaillible)
        PingResult r2 = pingViaCurl(url);
        if (r2.ok()) {
            log.info("Ping OK via curl.exe (HttpURLConnection avait echoue : {})", r1.message());
            return r2;
        }

        // Tout a echoue : retourne le message le plus pertinent
        return r1;
    }

    /** Strategie 1 : client HTTP Java natif (strictement HTTP/1.1). */
    private PingResult pingViaHttpUrlConnection(String url) {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(url);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String body = "";
            if (is != null) {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    body = r.lines().collect(Collectors.joining("\n"));
                }
            }

            if (status == 200 && body.contains("\"UP\"")) {
                return PingResult.success();
            }
            if (status == 200) {
                return PingResult.failure("Réponse inattendue (statut UP non confirmé).");
            }
            if (status == 503) {
                return PingResult.failure("Service temporairement indisponible.");
            }
            return PingResult.failure("HTTP " + status + " sur " + url);

        } catch (java.net.SocketTimeoutException e) {
            return PingResult.failure("Délai de connexion dépassé (5s).");
        } catch (java.net.ConnectException e) {
            return PingResult.failure("Serveur injoignable (host/port incorrect ?).");
        } catch (java.net.UnknownHostException e) {
            return PingResult.failure("Hôte inconnu : vérifiez l'URL.");
        } catch (Exception e) {
            return PingResult.failure("Erreur réseau : " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Strategie 2 : delegue a curl.exe (Windows 10+) ou curl (Linux/Mac).
     * Imbattable pour contourner les soucis de pile HTTP cote JVM.
     */
    private PingResult pingViaCurl(String url) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl",
                    "--silent",            // pas de barre de progression
                    "--show-error",        // mais affiche les erreurs
                    "--max-time", "5",     // timeout total 5s
                    "--http1.1",           // force HTTP/1.1
                    "--header", "Accept: application/json",
                    url
            );
            pb.redirectErrorStream(true);

            Process p = pb.start();
            String body;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                body = r.lines().collect(Collectors.joining("\n"));
            }
            boolean finished = p.waitFor(7, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return PingResult.failure("Délai dépassé (curl).");
            }
            int exit = p.exitValue();
            if (exit != 0) {
                return PingResult.failure("curl a échoué (code " + exit + ") : "
                        + (body.length() > 200 ? body.substring(0, 200) : body));
            }
            if (body.contains("\"UP\"")) {
                return PingResult.success();
            }
            return PingResult.failure("Réponse inattendue : "
                    + (body.length() > 100 ? body.substring(0, 100) + "..." : body));

        } catch (java.io.IOException e) {
            // curl pas trouve dans le PATH
            return PingResult.failure("curl indisponible : " + e.getMessage());
        } catch (Exception e) {
            return PingResult.failure("Erreur curl : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Verbes HTTP (appels metier sur /api/*)
    // ==================================================================

    public <T> T get(String path, Class<T> responseType) {
        return get(path, (TypeReference<T>) null, responseType);
    }

    public <T> T get(String path, TypeReference<T> typeRef) {
        return get(path, typeRef, null);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        HttpRequest req = requestBuilder(path)
                .POST(bodyOf(body))
                .header("Content-Type", "application/json")
                .build();
        return send(req, responseType, null);
    }

    public <T> T put(String path, Object body, Class<T> responseType) {
        HttpRequest req = requestBuilder(path)
                .PUT(bodyOf(body))
                .header("Content-Type", "application/json")
                .build();
        return send(req, responseType, null);
    }

    public <T> T patch(String path, Object body, Class<T> responseType) {
        HttpRequest req = requestBuilder(path)
                .method("PATCH", body == null ? BodyPublishers.noBody() : bodyOf(body))
                .header("Content-Type", "application/json")
                .build();
        return send(req, responseType, null);
    }

    public void delete(String path) {
        HttpRequest req = requestBuilder(path).DELETE().build();
        send(req, Void.class, null);
    }

    /**
     * GET sur un endpoint binaire (image, PDF, etc.). Renvoie les octets
     * bruts. Utilisé pour récupérer le logo image du reçu côté guichet.
     *
     * @throws ApiException si le serveur renvoie un code non-2xx (404 pour
     *                      un logo absent, par exemple).
     */
    public byte[] getBytes(String path) {
        HttpRequest req = requestBuilder(path).GET().build();
        try {
            HttpResponse<byte[]> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            if (sc < 200 || sc >= 300) {
                throw new ApiException(sc, "HTTP " + sc + " sur " + path);
            }
            return resp.body();
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(
                    "Erreur réseau sur " + path + " : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private HttpRequest.Builder requestBuilder(String path) {
        URI uri = URI.create(Config.getInstance().getApiUrl() + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        String token = Session.getInstance().getToken();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpRequest.BodyPublisher bodyOf(Object body) {
        try {
            String json = body == null ? "" : mapper.writeValueAsString(body);
            return BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException("Sérialisation impossible : " + e.getMessage());
        }
    }

    private <T> T get(String path, TypeReference<T> typeRef, Class<T> responseClass) {
        HttpRequest req = requestBuilder(path).GET().build();
        return send(req, responseClass, typeRef);
    }

    @SuppressWarnings("unchecked")
    private <T> T send(HttpRequest req, Class<T> responseClass, TypeReference<T> typeRef) {
        // Réessais transparents sur HttpConnectTimeoutException : la première
        // poignée de main TLS peut dépasser le délai si le pool de connexions
        // est froid (cas typique au chargement initial d'un écran).
        int attempt = 0;
        Exception lastConnectError = null;
        while (attempt <= MAX_RETRIES_ON_CONNECT_TIMEOUT) {
            try {
                HttpResponse<String> response = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                String body = response.body();

                if (status >= 200 && status < 300) {
                    if (responseClass == Void.class || body == null || body.isBlank()) {
                        return null;
                    }
                    if (typeRef != null) {
                        return mapper.readValue(body, typeRef);
                    }
                    return mapper.readValue(body, responseClass);
                }

                String message = extractErrorMessage(body, status);
                log.warn("API {} {} → {}: {}", req.method(), req.uri(), status, message);
                throw new ApiException(status, message);

            } catch (ApiException e) {
                throw e;
            } catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException e) {
                lastConnectError = e;
                attempt++;
                if (attempt > MAX_RETRIES_ON_CONNECT_TIMEOUT) break;
                log.warn("Timeout de connexion {} {} (tentative {} sur {}). Nouvelle tentative...",
                        req.method(), req.uri(), attempt, MAX_RETRIES_ON_CONNECT_TIMEOUT + 1);
                try {
                    Thread.sleep(500L * attempt); // backoff progressif : 0.5s, 1s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("Erreur appel API {} {}", req.method(), req.uri(), e);
                throw new ApiException("Erreur réseau : " + e.getMessage());
            }
        }

        log.error("Échec définitif après {} tentatives : {} {}",
                MAX_RETRIES_ON_CONNECT_TIMEOUT + 1, req.method(), req.uri(), lastConnectError);
        throw new ApiException(0, "Serveur injoignable (délai de connexion dépassé). "
                + "Vérifiez que le backend RTS Caisse répond à " + Config.getInstance().getApiUrl());
    }

    private String extractErrorMessage(String body, int status) {
        if (body == null || body.isBlank()) {
            return httpStatusLabel(status);
        }
        try {
            Map<String, Object> parsed = mapper.readValue(body, new TypeReference<>() {});
            Object msg = parsed.get("message");
            return msg != null ? msg.toString() : httpStatusLabel(status);
        } catch (Exception ex) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
    }

    private String httpStatusLabel(int status) {
        return switch (status) {
            case 401 -> "Session invalide ou expirée.";
            case 403 -> "Accès refusé.";
            case 404 -> "Ressource introuvable.";
            case 500 -> "Erreur interne du serveur.";
            case 503 -> "Service temporairement indisponible.";
            default  -> "Erreur HTTP " + status;
        };
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

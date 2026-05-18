package sn.rts.caisse.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utilitaire d'extraction de l'IP cliente reelle, derriere un reverse proxy.
 *
 * <p>Notre setup : Caddy en frontal qui forward via
 * {@code X-Real-IP} et {@code X-Forwarded-For}. {@code request.getRemoteAddr()}
 * renverrait l'IP du reseau Docker interne, inutile pour le rate limiting.</p>
 */
public final class ClientIpUtil {

    private ClientIpUtil() {}

    public static String get(HttpServletRequest request) {
        if (request == null) return "unknown";

        // 1) X-Real-IP est ce que Caddy met explicitement dans notre conf.
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // 2) X-Forwarded-For est plus standard. On prend le 1er = client.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }

        // 3) Fallback : adresse remote brute (cas test local sans proxy).
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}

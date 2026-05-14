package sn.rts.caisse.guichet.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.model.Dto.ParametresRecuDto;

/**
 * Cache singleton des paramètres de personnalisation du reçu.
 *
 * <p>Évite de refaire un appel HTTP à chaque impression : les paramètres
 * sont chargés à la première demande puis conservés pendant {@link #TTL_MS}
 * millisecondes. Au-delà, le prochain {@link #obtenir()} déclenche un
 * rechargement.</p>
 *
 * <p>Mode hors-ligne : si le backend est inaccessible, on conserve la
 * dernière valeur reçue. Si aucune valeur n'a jamais été chargée, on
 * renvoie {@code null} et le rendu utilise ses valeurs par défaut.</p>
 */
public final class ParametresRecuCache {

    private static final Logger log = LoggerFactory.getLogger(ParametresRecuCache.class);

    private static final long TTL_MS = 60_000L; // 1 minute

    private static final ParametresRecuCache INSTANCE = new ParametresRecuCache();

    public static ParametresRecuCache getInstance() {
        return INSTANCE;
    }

    private ParametresRecuDto cached;
    private byte[] cachedLogo;
    private long lastFetchAt = 0L;

    private ParametresRecuCache() {}

    /**
     * Renvoie les paramètres en cache (chargement initial automatique).
     * Peut renvoyer {@code null} si le backend n'a jamais répondu.
     */
    public synchronized ParametresRecuDto obtenir() {
        long now = System.currentTimeMillis();
        if (cached == null || now - lastFetchAt > TTL_MS) {
            recharger();
        }
        return cached;
    }

    /** Renvoie le logo image en cache, ou null s'il n'y en a pas. */
    public synchronized byte[] obtenirLogo() {
        // S'assure que `cached` est à jour, ce qui amorce aussi cachedLogo.
        obtenir();
        return cachedLogo;
    }

    /** Force un rechargement, ignorant le TTL. */
    public synchronized void rafraichir() {
        recharger();
    }

    private void recharger() {
        try {
            CaisseApi api = CaisseApi.getInstance();
            ParametresRecuDto fresh = api.obtenirParametresRecu();
            byte[] freshLogo = fresh != null && fresh.logoPresent
                    ? api.obtenirLogoRecu() : null;
            this.cached = fresh;
            this.cachedLogo = freshLogo;
            this.lastFetchAt = System.currentTimeMillis();
            log.debug("Paramètres du reçu rechargés (logo={} octets).",
                    freshLogo == null ? 0 : freshLogo.length);
        } catch (Exception e) {
            log.warn("Impossible de charger les paramètres du reçu, fallback "
                    + "sur la dernière valeur connue ou les valeurs par "
                    + "défaut : {}", e.getMessage());
        }
    }
}

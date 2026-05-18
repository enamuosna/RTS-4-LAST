package sn.rts.caisse.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter en memoire pour {@code POST /api/auth/login}, pour bloquer
 * les attaques par force brute.
 *
 * <h2>Politique</h2>
 * <ul>
 *   <li>Max <b>{@value #MAX_FAILURES} echecs</b> dans une fenetre glissante
 *       de <b>{@value #WINDOW_MINUTES} minutes</b>, par adresse IP cliente.</li>
 *   <li>Le compteur est <b>remis a zero</b> apres un login reussi.</li>
 *   <li>Les entrees expirees sont evictees a chaque acces (pas de scheduler
 *       requis, eviction lazy paresseuse).</li>
 * </ul>
 *
 * <h2>Pourquoi en memoire</h2>
 * <p>L'app tourne en single-instance (1 conteneur Docker). Si on passe en
 * cluster, il faudra basculer sur un store distribue (Redis, Bucket4j-Redis).
 * Pour l'instant, ConcurrentHashMap + ArrayDeque suffit largement.</p>
 *
 * <h2>Pourquoi un slim deque par IP</h2>
 * <p>On stocke uniquement les <b>timestamps des echecs recents</b>. A chaque
 * verification on retire les timestamps plus vieux que la fenetre. Une fois
 * la fenetre videe, l'entree disparait. Memoire bornée naturellement :
 * a peine quelques milliers d'octets pour des milliers d'IPs actives.</p>
 */
@Slf4j
@Service
public class LoginRateLimitService {

    /** Nombre maximal d'echecs avant blocage. */
    public static final int MAX_FAILURES = 5;

    /** Fenetre glissante d'observation, en minutes. */
    public static final long WINDOW_MINUTES = 15;

    private static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

    /** Map IP -> deque des timestamps d'echecs dans la fenetre. */
    private final ConcurrentMap<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    /**
     * Verifie si l'IP est actuellement bloquee.
     *
     * @return {@code Optional.of(secondes-a-attendre)} si bloquee, sinon
     *         {@code Optional.empty()} si la tentative est autorisee.
     */
    public Optional<Long> checkBlocked(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return Optional.empty();
        Deque<Instant> deque = failuresByIp.get(clientIp);
        if (deque == null) return Optional.empty();

        synchronized (deque) {
            evictExpired(deque);
            if (deque.size() < MAX_FAILURES) return Optional.empty();
            Instant oldest = deque.peekFirst();
            long secondsRemaining = oldest != null
                    ? Math.max(1, Duration.between(Instant.now(), oldest.plus(WINDOW)).getSeconds())
                    : 1;
            return Optional.of(secondsRemaining);
        }
    }

    /**
     * Enregistre un echec de login pour cette IP. Si on franchit le seuil,
     * la prochaine {@link #checkBlocked(String)} renverra non vide.
     */
    public void recordFailure(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        Deque<Instant> deque = failuresByIp.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (deque) {
            evictExpired(deque);
            deque.addLast(Instant.now());
            if (deque.size() >= MAX_FAILURES) {
                log.warn("[RATE-LIMIT] IP {} bloquee : {} echecs login dans la fenetre de {} min",
                        clientIp, deque.size(), WINDOW_MINUTES);
            }
        }
    }

    /**
     * Reset complet du compteur pour cette IP (typiquement apres un login
     * reussi). Liberation immediate de la map si plus de tentatives en cours.
     */
    public void recordSuccess(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        failuresByIp.remove(clientIp);
    }

    /** Retire les timestamps en dehors de la fenetre glissante. */
    private void evictExpired(Deque<Instant> deque) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}

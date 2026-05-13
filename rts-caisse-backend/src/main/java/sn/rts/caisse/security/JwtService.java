package sn.rts.caisse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import sn.rts.caisse.model.Utilisateur;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Génération et validation des JWT signés HMAC-SHA256.
 *
 * <p>Le secret de signature ({@code app.jwt.secret}) est attendu en Base64,
 * soit en variante standard (RFC 4648 §4, alphabet {@code + /}) soit en
 * variante URL-safe (RFC 4648 §5, alphabet {@code - _}). Cette classe
 * normalise la chaîne reçue avant de décoder, de sorte que les secrets
 * générés par {@code openssl rand -base64} ou par {@code Convert.ToBase64UrlString}
 * fonctionnent indifféremment.</p>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs,
                      @Value("${app.jwt.issuer}") String issuer) {

        String normalized = secret
                .trim()
                .replace('-', '+')
                .replace('_', '/');

        int padding = (4 - normalized.length() % 4) % 4;
        if (padding > 0) {
            normalized = normalized + "=".repeat(padding);
        }

        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(normalized));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    // ----------------------------------------------------------------
    //  Génération
    // ----------------------------------------------------------------

    public String generateToken(Utilisateur utilisateur) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(utilisateur.getLogin())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .claims(Map.of(
                        "uid",       utilisateur.getId(),
                        "matricule", utilisateur.getMatricule(),
                        "role",      utilisateur.getRole().name(),
                        "nom",       utilisateur.getNomComplet()
                ))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    // ----------------------------------------------------------------
    //  Lecture / validation
    // ----------------------------------------------------------------

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String login = extractUsername(token);
            return login != null
                    && login.equals(userDetails.getUsername())
                    && !isExpired(token);
        } catch (JwtException ex) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        Date exp = extractClaim(token, Claims::getExpiration);
        return exp != null && exp.before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}

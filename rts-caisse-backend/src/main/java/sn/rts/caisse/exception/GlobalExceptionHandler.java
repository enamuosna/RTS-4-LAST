package sn.rts.caisse.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Convertit les exceptions en réponses JSON homogènes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Identifiants invalides.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Authentification requise : " + ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Accès refusé : privilèges insuffisants.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Violation d'intégrité : {}", cause);
        return build(HttpStatus.CONFLICT, messageDoublonExplicite(cause));
    }

    /**
     * Parse le message PostgreSQL pour indiquer précisément quel champ
     * est en doublon. Le message brut ressemble à :
     *   ERROR: duplicate key value violates unique constraint "uk_xxx"
     *   Detail: Key (login)=(moussa) already exists.
     * On extrait le nom du champ et la valeur pour un retour utilisateur clair.
     */
    private static String messageDoublonExplicite(String causePsql) {
        if (causePsql == null) {
            return "Violation d'intégrité de données (doublon ou contrainte).";
        }
        // Pattern PostgreSQL : Key (champ)=(valeur) already exists.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("Key \\(([^)]+)\\)=\\(([^)]*)\\)")
                .matcher(causePsql);
        if (m.find()) {
            String champ = libelleChamp(m.group(1));
            String valeur = m.group(2);
            return "Cette valeur existe déjà : " + champ + " = « " + valeur + " ». "
                    + "Choisissez une autre valeur.";
        }
        // Fallback : on devine quand même selon des mots-clés de la contrainte
        String low = causePsql.toLowerCase();
        if (low.contains("login")) {
            return "Cet identifiant est déjà utilisé. Choisissez-en un autre.";
        }
        if (low.contains("matricule")) {
            return "Ce matricule est déjà utilisé. Choisissez-en un autre.";
        }
        if (low.contains("email")) {
            return "Cet e-mail est déjà utilisé.";
        }
        if (low.contains("telephone")) {
            return "Ce numéro de téléphone est déjà utilisé.";
        }
        return "Doublon détecté : une donnée saisie est déjà utilisée.";
    }

    private static String libelleChamp(String technique) {
        return switch (technique.toLowerCase().trim()) {
            case "login"     -> "identifiant";
            case "matricule" -> "matricule";
            case "email"     -> "e-mail";
            case "telephone" -> "téléphone";
            case "code"      -> "code";
            case "ninea"     -> "NINEA";
            default          -> technique;
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> champs = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            champs.put(fe.getField(), fe.getDefaultMessage());
        }
        ApiError body = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Validation échouée",
                LocalDateTime.now(),
                champs);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex) {
        log.error("Erreur interne non gérée", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur interne du serveur. Contactez l'administrateur.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                new ApiError(status.value(), message, LocalDateTime.now(), null));
    }

    /** Corps d'erreur standardisé. */
    public record ApiError(int status,
                           String message,
                           LocalDateTime timestamp,
                           Map<String, String> erreurs) {
    }
}

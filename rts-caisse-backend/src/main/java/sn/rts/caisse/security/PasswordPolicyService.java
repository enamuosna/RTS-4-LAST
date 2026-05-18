package sn.rts.caisse.security;

import org.springframework.stereotype.Service;
import sn.rts.caisse.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Valide qu'un mot de passe respecte la politique de complexite RTS.
 *
 * <h2>Regles</h2>
 * <ul>
 *   <li>Au moins {@value #MIN_LENGTH} caracteres</li>
 *   <li>Au moins 1 minuscule</li>
 *   <li>Au moins 1 majuscule</li>
 *   <li>Au moins 1 chiffre</li>
 *   <li>Au moins 1 caractere special (parmi {@value #SPECIAL_CHARS})</li>
 *   <li>Pas d'espace</li>
 * </ul>
 *
 * <p>Applique :
 * <ul>
 *   <li>a la creation d'un utilisateur (UtilisateurService.creer)</li>
 *   <li>au changement de mot de passe (changerMotDePasse)</li>
 *   <li>a la reinitialisation par un admin (reinitialiser-mdp-dialog)</li>
 * </ul>
 * </p>
 *
 * <p>Le frontend duplique la meme regle pour donner un retour immediat
 * a l'utilisateur (checklist en temps reel), mais le backend reste
 * l'autorite finale : aucun mdp faible ne peut entrer en base, meme si
 * un client a contourne le frontend.</p>
 */
@Service
public class PasswordPolicyService {

    public static final int MIN_LENGTH = 12;
    public static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{};':\",.<>/?\\|`~";

    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT     = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL =
            Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\",.<>/?\\\\|`~]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    /**
     * Verifie que le mot de passe respecte toutes les regles de complexite.
     * Levee une {@link BusinessException} avec un message qui liste TOUTES
     * les regles violees (pas seulement la premiere), pour aider l'utilisateur.
     */
    public void validate(String motDePasse) {
        if (motDePasse == null) {
            throw new BusinessException("Le mot de passe est obligatoire.");
        }
        List<String> violations = new ArrayList<>();
        if (motDePasse.length() < MIN_LENGTH) {
            violations.add("au moins " + MIN_LENGTH + " caracteres (actuellement "
                    + motDePasse.length() + ")");
        }
        if (!LOWERCASE.matcher(motDePasse).find()) {
            violations.add("au moins 1 minuscule (a-z)");
        }
        if (!UPPERCASE.matcher(motDePasse).find()) {
            violations.add("au moins 1 majuscule (A-Z)");
        }
        if (!DIGIT.matcher(motDePasse).find()) {
            violations.add("au moins 1 chiffre (0-9)");
        }
        if (!SPECIAL.matcher(motDePasse).find()) {
            violations.add("au moins 1 caractere special (ex: " + SPECIAL_CHARS.substring(0, 10) + "...)");
        }
        if (WHITESPACE.matcher(motDePasse).find()) {
            violations.add("aucun espace");
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(
                    "Mot de passe trop faible. Il doit contenir : "
                            + String.join(", ", violations) + ".");
        }
    }
}

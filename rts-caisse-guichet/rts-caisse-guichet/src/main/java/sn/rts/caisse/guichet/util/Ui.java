package sn.rts.caisse.guichet.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Méthodes statiques utilitaires : formatage, boîtes de dialogue.
 */
public final class Ui {

    private static final Locale FR_SN = Locale.of("fr", "SN");
    private static final NumberFormat CURRENCY = NumberFormat.getNumberInstance(FR_SN);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_TIME_FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm:ss");

    static {
        CURRENCY.setMinimumFractionDigits(0);
        CURRENCY.setMaximumFractionDigits(0);
    }

    private Ui() {}

    // ==================================================================
    //  Formatage
    // ==================================================================

    public static String formatMontant(BigDecimal montant) {
        if (montant == null) return "—";
        return CURRENCY.format(montant) + " FCFA";
    }

    public static String formatMontant(double montant) {
        return CURRENCY.format(montant) + " FCFA";
    }

    public static String formatDateTime(LocalDateTime date) {
        return date == null ? "—" : date.format(DATE_TIME);
    }

    public static String formatDateTimeFull(LocalDateTime date) {
        return date == null ? "—" : date.format(DATE_TIME_FULL);
    }

    // ==================================================================
    //  Boîtes de dialogue
    // ==================================================================

    public static void info(String titre, String message) {
        RtsDialog.message(RtsDialog.Type.INFO, titre, message);
    }

    public static void erreur(String titre, String message) {
        RtsDialog.message(RtsDialog.Type.ERROR, titre, message);
    }

    public static void succes(String titre, String message) {
        RtsDialog.message(RtsDialog.Type.SUCCESS, titre, message);
    }

    public static void avertissement(String titre, String message) {
        RtsDialog.message(RtsDialog.Type.WARNING, titre, message);
    }

    public static boolean confirmer(String titre, String message) {
        return RtsDialog.confirm(titre, message);
    }

    public static Optional<String> demanderTexte(String titre, String message, String defaut) {
        return RtsDialog.prompt(titre, message, defaut);
    }

    /** Parse un montant saisi par l'utilisateur (accepte "1 500" ou "1500.00"). */
    public static BigDecimal parseMontant(String input) {
        if (input == null || input.isBlank()) return null;
        String cleaned = input.replace(" ", "").replace(",", ".");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package sn.rts.caisse.guichet.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

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
        showAlert(Alert.AlertType.INFORMATION, titre, message);
    }

    public static void erreur(String titre, String message) {
        showAlert(Alert.AlertType.ERROR, titre, message);
    }

    public static boolean confirmer(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("RTS Caisse - " + titre);
        alert.setHeaderText(titre);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static Optional<String> demanderTexte(String titre, String message, String defaut) {
        TextInputDialog dialog = new TextInputDialog(defaut == null ? "" : defaut);
        dialog.setTitle("RTS Caisse - " + titre);
        dialog.setHeaderText(titre);
        dialog.setContentText(message);
        return dialog.showAndWait();
    }

    private static void showAlert(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("RTS Caisse - " + titre);
        alert.setHeaderText(titre);
        alert.setContentText(message);
        alert.showAndWait();
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

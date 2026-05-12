package sn.rts.caisse.guichet.print;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.ApiException;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.model.Dto.EnvoiWhatsAppResponse;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseResponse;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Exporte un reçu en PNG (utilitaire local) et déclenche l'envoi WhatsApp
 * via le <b>backend Spring Boot</b> qui appelle l'API WhatsApp Business
 * Cloud de Meta.
 *
 * <h2>Workflow caissier (clic 📱)</h2>
 * <ol>
 *   <li>Récupération ou saisie du numéro WhatsApp destinataire.</li>
 *   <li>Appel HTTP au backend : {@code POST /api/operations/{id}/whatsapp}.</li>
 *   <li>Le backend génère le PDF, l'uploade chez Meta, envoie le message.</li>
 *   <li>Notification "Reçu envoyé ✓" ou message d'erreur lisible.</li>
 * </ol>
 *
 * <p><b>Aucune ouverture</b> de WhatsApp Web ou Desktop côté caissier.
 * <b>Aucune action utilisateur</b> au-delà du clic initial : tout l'envoi
 * est piloté serveur-to-Meta.</p>
 */
public final class RecuExporter {

    private static final Logger log = LoggerFactory.getLogger(RecuExporter.class);

    /** Dossier de stockage local des reçus PNG (impression, archive). */
    private static final Path RECUS_DIR = Paths.get(
            System.getProperty("user.home"),
            "Documents", "RTS-Caisse", "recus");

    private static final double SNAPSHOT_SCALE = 2.5;

    private RecuExporter() {}

    // ==================================================================
    //  Capture image (utilitaire pour PNG local)
    // ==================================================================

    private static BufferedImage capturerImageRecu(OperationCaisseResponse op) {
        Node recu = PrintRecu.construireRecu(op);

        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(recu);
        root.setStyle("-fx-background-color: white;");
        new Scene(root);
        root.applyCss();
        root.layout();

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        params.setTransform(javafx.scene.transform.Transform.scale(
                SNAPSHOT_SCALE, SNAPSHOT_SCALE));

        WritableImage fxImage = new WritableImage(
                (int) Math.round(recu.getBoundsInLocal().getWidth() * SNAPSHOT_SCALE),
                (int) Math.round(recu.getBoundsInLocal().getHeight() * SNAPSHOT_SCALE));
        recu.snapshot(params, fxImage);
        return SwingFXUtils.fromFXImage(fxImage, null);
    }

    /**
     * Génère une image PNG du reçu et la sauvegarde dans {@link #RECUS_DIR}.
     * Conservé pour archivage et impression locale (ne sert plus pour WhatsApp).
     */
    public static Path exporterPng(OperationCaisseResponse op) {
        try {
            BufferedImage swingImage = capturerImageRecu(op);
            Files.createDirectories(RECUS_DIR);
            Path destination = RECUS_DIR.resolve("recu-" + sanitize(op.numeroRecu) + ".png");
            ImageIO.write(swingImage, "png", destination.toFile());
            log.info("Reçu PNG exporté : {}", destination);
            return destination;
        } catch (Exception e) {
            log.error("Échec export PNG du reçu n° {} : {}",
                    op.numeroRecu, e.getMessage(), e);
            return null;
        }
    }

    // ==================================================================
    //  Envoi WhatsApp via backend Cloud API
    // ==================================================================

    /**
     * Déclenche l'envoi du reçu sur WhatsApp via le backend RTS.
     *
     * <p>Cette méthode :</p>
     * <ul>
     *   <li>récupère ou demande le numéro WhatsApp destinataire,</li>
     *   <li>appelle le backend de manière asynchrone (l'UI ne fige pas),</li>
     *   <li>affiche une notification de succès ou d'échec.</li>
     * </ul>
     */
    public static boolean envoyerWhatsApp(OperationCaisseResponse op) {
        if (op == null) return false;

        // 1) Numéro destinataire (champ client ou saisie à la volée)
        String phoneRaw = op.clientTelephone;
        if (phoneRaw == null || phoneRaw.isBlank()) {
            String hint = (op.clientRaisonSociale != null && !op.clientRaisonSociale.isBlank())
                    ? "Aucun numéro WhatsApp enregistré pour le client \""
                            + op.clientRaisonSociale + "\".\n\n"
                    : "Aucun client n'est associé à cette opération.\n\n";

            Optional<String> input = Ui.demanderTexte(
                    "Envoi WhatsApp",
                    hint + "Saisissez le numéro WhatsApp du destinataire :\n"
                            + "(formats acceptés : 77 123 45 67, +221 77 123 45 67)",
                    "");
            if (input.isEmpty() || input.get().isBlank()) {
                return false;
            }
            phoneRaw = input.get();
        }

        final String numeroFinal = phoneRaw;

        // 2) Appel backend asynchrone : l'UI reste réactive pendant les
        //    2 appels HTTP que fait le backend vers Meta (~1 à 3 secondes).
        AsyncRunner.run(
                () -> CaisseApi.getInstance().envoyerOperationWhatsApp(op.id, numeroFinal),
                reponse -> afficherResultat(op, reponse),
                throwable -> afficherErreur(numeroFinal, throwable));

        return true;
    }

    // ==================================================================
    //  Notifications utilisateur
    // ==================================================================

    private static void afficherResultat(OperationCaisseResponse op,
                                         EnvoiWhatsAppResponse reponse) {
        if (reponse == null) {
            Ui.erreur("WhatsApp",
                    "Le serveur n'a pas répondu correctement.");
            return;
        }
        if (reponse.envoye) {
            Ui.info("Reçu envoyé sur WhatsApp ✓",
                    "Le reçu n° " + op.numeroRecu + " a été envoyé à\n"
                            + "+" + reponse.destinataire + "\n\n"
                            + "Identifiant Meta : " + reponse.messageId);
        } else {
            Ui.erreur("Échec de l'envoi WhatsApp",
                    "Destinataire : +"
                            + (reponse.destinataire != null ? reponse.destinataire : "?")
                            + "\n\n"
                            + (reponse.messageErreur != null
                                    ? reponse.messageErreur
                                    : "Cause inconnue."));
        }
    }

    private static void afficherErreur(String numero, Throwable e) {
        String message;
        if (e instanceof ApiException api) {
            message = api.getMessage();
        } else {
            message = e.getMessage() != null ? e.getMessage() : "Erreur inconnue.";
        }
        Ui.erreur("WhatsApp - erreur",
                "Échec de l'envoi à " + numero + " :\n\n" + message);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private static String sanitize(String s) {
        if (s == null) return "sansnumero";
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}

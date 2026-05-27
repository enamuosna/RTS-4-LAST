package sn.rts.caisse.guichet.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.ApiException;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.model.Dto.BanqueDTO;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;
import sn.rts.caisse.guichet.model.Dto.VersementResponse;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Ui;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controleur du modal "Versement bancaire" : enregistre un depot
 * d'especes / virement interne vers une banque, avec upload du bordereau
 * (PDF, JPG ou PNG - max 5 Mo).
 *
 * <p>Le journal de la session en cours est passe optionnellement par
 * l'appelant (CaissierController) ; s'il existe, le versement y est
 * rattache pour apparaitre dans l'export Excel du journal.</p>
 */
public class VersementController {

    private static final Logger log = LoggerFactory.getLogger(VersementController.class);

    /** Taille max du fichier (5 Mo, identique a la validation backend). */
    private static final long TAILLE_MAX = 5L * 1024 * 1024;

    /** Types MIME acceptes. */
    private static final Set<String> TYPES_AUTORISES = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png");

    @FXML private Label              caisseLabel;
    @FXML private ComboBox<BanqueDTO> banqueCombo;
    @FXML private TextField          montantField;
    @FXML private TextField          bordereauField;
    @FXML private DatePicker         datePicker;
    @FXML private TextArea           notesArea;
    @FXML private Button             choisirFichierButton;
    @FXML private Label              fichierLabel;
    @FXML private Button             enregistrerButton;

    private final CaisseApi api = CaisseApi.getInstance();

    private CaisseDTO caisse;
    /** Journal optionnel : si renseigne, le versement y est rattache. */
    private Long journalId;
    private Consumer<VersementResponse> onSuccess;

    private List<BanqueDTO> toutesBanques = List.of();
    private File fichierSelectionne;
    private String typeMimeSelectionne;

    @FXML
    public void initialize() {
        banqueCombo.setConverter(new StringConverter<BanqueDTO>() {
            @Override public String toString(BanqueDTO b) {
                return b == null ? "" : (b.code + " - " + b.libelle);
            }
            @Override public BanqueDTO fromString(String s) {
                if (s == null || s.isBlank()) return null;
                String t = s.trim();
                return toutesBanques.stream()
                        .filter(b -> t.equalsIgnoreCase(b.code)
                                || t.equalsIgnoreCase(b.code + " - " + b.libelle))
                        .findFirst().orElse(null);
            }
        });
    }

    public void initialiser(CaisseDTO caisse, Long journalId,
                             Consumer<VersementResponse> onSuccess) {
        this.caisse    = caisse;
        this.journalId = journalId;
        this.onSuccess = onSuccess;
        if (caisse != null) {
            caisseLabel.setText(caisse.code + " - " + caisse.libelle);
        }
        chargerBanques();
    }

    private void chargerBanques() {
        AsyncRunner.run(
                () -> api.listerBanques(),
                banques -> {
                    toutesBanques = banques == null ? List.of() : banques;
                    banqueCombo.setItems(FXCollections.observableArrayList(toutesBanques));
                },
                e -> {
                    log.error("Banques indisponibles", e);
                    Ui.erreur("Banques indisponibles",
                            "Impossible de charger la liste des banques.");
                });
    }

    @FXML
    public void onChoisirFichier() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selectionner le bordereau bancaire");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documents (PDF, JPG, PNG)",
                        "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("PDF",   "*.pdf"),
                new FileChooser.ExtensionFilter("JPEG",  "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG",   "*.png"));
        Stage stage = (Stage) choisirFichierButton.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        if (file.length() > TAILLE_MAX) {
            Ui.erreur("Fichier trop volumineux",
                    "Le fichier fait " + (file.length() / 1024) + " Ko, max 5 Mo.");
            return;
        }
        String mime = deduireTypeMime(file.getName());
        if (mime == null || !TYPES_AUTORISES.contains(mime)) {
            Ui.erreur("Format non supporte",
                    "Seuls les fichiers PDF, JPG et PNG sont acceptes.");
            return;
        }
        fichierSelectionne = file;
        typeMimeSelectionne = mime;
        fichierLabel.setText(file.getName() + "  (" + (file.length() / 1024) + " Ko)");
    }

    /** Devine le type MIME a partir de l'extension du nom de fichier. */
    private static String deduireTypeMime(String nom) {
        String n = nom.toLowerCase();
        if (n.endsWith(".pdf"))                       return "application/pdf";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png"))                       return "image/png";
        return null;
    }

    @FXML
    public void onEnregistrer() {
        if (caisse == null) {
            Ui.erreur("Aucune caisse", "Aucune caisse n'est selectionnee.");
            return;
        }
        BanqueDTO banque = banqueCombo.getValue();
        if (banque == null) {
            Ui.erreur("Banque manquante", "Selectionnez la banque destinataire.");
            return;
        }
        BigDecimal montant = Ui.parseMontant(montantField.getText());
        if (montant == null || montant.signum() <= 0) {
            Ui.erreur("Montant invalide",
                    "Saisissez un montant strictement positif.");
            return;
        }
        String bordereau = bordereauField.getText() == null
                ? "" : bordereauField.getText().trim();
        if (bordereau.isEmpty()) {
            Ui.erreur("Bordereau manquant",
                    "Saisissez le numero du bordereau bancaire.");
            return;
        }
        if (fichierSelectionne == null) {
            Ui.erreur("Bordereau manquant",
                    "Ajoutez le scan / photo du bordereau (PDF, JPG ou PNG).");
            return;
        }

        // Date+heure : si l'utilisateur a saisi une date, on met 12:00 par
        // defaut. Sinon, on envoie null et le backend met now().
        String dateIso = null;
        if (datePicker.getValue() != null) {
            dateIso = datePicker.getValue().atTime(LocalTime.NOON).toString();
        }

        final byte[] contenu;
        try {
            contenu = Files.readAllBytes(fichierSelectionne.toPath());
        } catch (Exception ex) {
            Ui.erreur("Lecture du fichier", "Impossible de lire le fichier : "
                    + ex.getMessage());
            return;
        }
        final String notes = notesArea.getText();
        final String dateIsoFinal = dateIso;

        enregistrerButton.setDisable(true);
        AsyncRunner.run(
                () -> api.enregistrerVersement(
                        caisse.id, banque.id, journalId,
                        montant, bordereau, dateIsoFinal, notes,
                        fichierSelectionne.getName(),
                        typeMimeSelectionne, contenu),
                vers -> {
                    enregistrerButton.setDisable(false);
                    Ui.info("Versement enregistre",
                            "Versement de " + Ui.formatMontant(montant)
                                    + " enregistre avec le bordereau "
                                    + bordereau + ".");
                    if (onSuccess != null) onSuccess.accept(vers);
                    fermerModal();
                },
                e -> {
                    enregistrerButton.setDisable(false);
                    String msg = e instanceof ApiException
                            ? e.getMessage()
                            : (e.getMessage() != null
                                ? e.getMessage() : "Erreur inconnue");
                    log.warn("Echec enregistrement versement : {}", msg, e);
                    Ui.erreur("Echec de l'enregistrement", msg);
                });
    }

    @FXML
    public void onAnnuler() {
        fermerModal();
    }

    private void fermerModal() {
        Stage stage = (Stage) enregistrerButton.getScene().getWindow();
        stage.close();
    }
}

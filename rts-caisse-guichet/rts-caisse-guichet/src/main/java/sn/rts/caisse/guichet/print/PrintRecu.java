package sn.rts.caisse.guichet.print;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.model.Dto.OperationCaisseResponse;
import sn.rts.caisse.guichet.util.Ui;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Construction et impression du reçu de caisse.
 *
 * <p>Évolutions (mai 2026) :</p>
 * <ul>
 *   <li>En-tête en disposition verticale : logo en haut à gauche,
 *       puis informations de l'entreprise juste en dessous (centrées).</li>
 *   <li>Reçu centré horizontalement sur la page d'impression
 *       (via {@link StackPane}).</li>
 *   <li>Nouveau bloc « BANQUE ÉMETTRICE » affiché uniquement pour
 *       les règlements par chèque ou virement.</li>
 * </ul>
 */
public final class PrintRecu {

    private static final Logger log = LoggerFactory.getLogger(PrintRecu.class);

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final double LARGEUR_RECU = 380;

    private static final Color RTS_BLUE  = Color.web("#0a4d8c");
    private static final Color GRAY_900  = Color.web("#0f172a");
    private static final Color GRAY_700  = Color.web("#334155");
    private static final Color GRAY_500  = Color.web("#64748b");
    private static final Color GRAY_300  = Color.web("#cbd5e1");

    private PrintRecu() {}

    // ==================================================================
    //  Impression
    // ==================================================================

    public static boolean imprimer(OperationCaisseResponse op) {
        Node recu = construireRecu(op);

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Ui.erreur("Impression impossible",
                    "Aucune imprimante n'est configurée sur ce poste.");
            return false;
        }
        boolean showDialog = job.showPrintDialog(null);
        if (!showDialog) {
            return false;
        }

        PageLayout layout = job.getJobSettings().getPageLayout();
        double maxWidth  = layout.getPrintableWidth();
        double maxHeight = layout.getPrintableHeight();

        // On force le reçu à occuper 92% de la largeur imprimable de la page,
        // qu'il soit plus petit ou plus grand au départ. Ça évite à la fois :
        //  - le reçu minuscule sur une A4 (380 px sur 595 pt = 64%)
        //  - les marges asymétriques que Windows applique parfois quand on
        //    laisse le centrage à StackPane.
        Scene scene = new Scene(new StackPane(recu));
        scene.getRoot().applyCss();
        ((javafx.scene.Parent) scene.getRoot()).layout();

        double recuWidth = recu.getBoundsInLocal().getWidth();
        if (recuWidth > 0) {
            double scale = (maxWidth * 0.92) / recuWidth;
            recu.getTransforms().add(
                    javafx.scene.transform.Transform.scale(scale, scale));
        }

        boolean printed = job.printPage(recu);
        if (printed) {
            job.endJob();
        }
        return printed;
    }

    public static void listerImprimantes() {
        Printer.getAllPrinters().forEach(p ->
                System.out.println("Imprimante disponible : " + p.getName()));
    }

    // ==================================================================
    //  Construction du reçu
    // ==================================================================

    public static Node construireRecu(OperationCaisseResponse op) {
        VBox root = new VBox(0);
        root.setPrefWidth(LARGEUR_RECU);
        root.setMaxWidth(LARGEUR_RECU);
        root.setMinWidth(LARGEUR_RECU);
        root.setBackground(new Background(new BackgroundFill(
                Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        root.setBorder(new Border(new BorderStroke(
                GRAY_900, BorderStrokeStyle.SOLID,
                new CornerRadii(4), new BorderWidths(1.5))));
        root.setPadding(new Insets(16, 18, 16, 18));

        root.getChildren().add(construireEntete());
        root.getChildren().add(separateurDouble());
        root.getChildren().add(construireNumero(op));
        root.getChildren().add(separateur());
        root.getChildren().add(construireMetadonnees(op));
        root.getChildren().add(separateur());

        if (aDesInfosClient(op)) {
            root.getChildren().add(construireBlocClient(op));
            root.getChildren().add(separateur());
        }

        root.getChildren().add(construireDetailsOperation(op));

        if (aDesInfosBanque(op)) {
            root.getChildren().add(separateur());
            root.getChildren().add(construireBlocBanque(op));
        }

        root.getChildren().add(separateur());
        root.getChildren().add(construireMontant(op));

        if (op.annulee) {
            root.getChildren().add(construireBandeauAnnulee(op));
        }
        root.getChildren().add(separateurDouble());
        root.getChildren().add(construirePiedDePage(op));
        return root;
    }

    // ==================================================================
    //  En-tête : logo haut-gauche + infos entreprise en dessous (centrées)
    // ==================================================================

    private static Node construireEntete() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.TOP_CENTER);
        header.setPadding(new Insets(0, 0, 8, 0));

        // ── Ligne 1 : logo aligné à gauche ──
        HBox logoRow = new HBox();
        logoRow.setAlignment(Pos.CENTER_LEFT);
        ImageView logo = chargerLogo();
        if (logo != null) {
            logoRow.getChildren().add(logo);
        }
        header.getChildren().add(logoRow);

        // ── Ligne 2 : informations entreprise centrées sous le logo ──
        VBox infos = new VBox(1);
        infos.setAlignment(Pos.CENTER);

        Label raisonSociale = new Label("SOCIÉTÉ NATIONALE DE RADIODIFFUSION");
        raisonSociale.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        raisonSociale.setTextFill(RTS_BLUE);

        Label sousTitre = new Label("TÉLÉVISION DU SÉNÉGAL");
        sousTitre.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        sousTitre.setTextFill(RTS_BLUE);

        Label loi = new Label("Créée par la loi n° 92-02 du 06 janvier 1992");
        loi.setFont(Font.font("Arial", 8));
        loi.setTextFill(GRAY_700);

        Label capital = new Label("Capital : 7 milliards FCFA");
        capital.setFont(Font.font("Arial", 8));
        capital.setTextFill(GRAY_700);

        Label adresse1 = new Label("Triangle Sud — Tél. (221) 33 849 12 12");
        adresse1.setFont(Font.font("Arial", 8));
        adresse1.setTextFill(GRAY_700);

        Label adresse2 = new Label("B.P. 1765 — DAKAR");
        adresse2.setFont(Font.font("Arial", 8));
        adresse2.setTextFill(GRAY_700);

        Label ninea = new Label("NINEA : 2059782 2G3");
        ninea.setFont(Font.font("Arial", FontWeight.BOLD, 8));
        ninea.setTextFill(GRAY_700);

        infos.getChildren().addAll(
                raisonSociale, sousTitre, loi, capital, adresse1, adresse2, ninea);
        header.getChildren().add(infos);

        return header;
    }

    private static ImageView chargerLogo() {
        String[] cheminsCandidats = {
                "/fxml/rts-logo.jpg",
                "/images/rts-logo.jpg",
                "/images/rts-logo.png",
                "/rts-logo.jpg"
        };
        for (String chemin : cheminsCandidats) {
            URL url = PrintRecu.class.getResource(chemin);
            if (url != null) {
                try {
                    Image img = new Image(url.toExternalForm(), 56, 56, true, true);
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(56);
                    iv.setFitHeight(56);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    return iv;
                } catch (Exception e) {
                    log.warn("Logo trouvé à {} mais illisible : {}",
                            chemin, e.getMessage());
                }
            }
        }
        log.warn("Logo RTS introuvable dans le classpath, en-tête sans image.");
        return null;
    }

    // ==================================================================
    //  Numéro de reçu
    // ==================================================================

    private static Node construireNumero(OperationCaisseResponse op) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 6, 0));

        Label libelle = new Label("REÇU");
        libelle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        libelle.setTextFill(GRAY_900);

        Label numero = new Label("N°  " + (op.numeroRecu == null ? "—" : op.numeroRecu));
        numero.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        numero.setTextFill(RTS_BLUE);

        box.getChildren().addAll(libelle, numero);
        return box;
    }

    // ==================================================================
    //  Métadonnées (date, caisse, agent)
    // ==================================================================

    private static Node construireMetadonnees(OperationCaisseResponse op) {
        VBox box = new VBox(3);
        box.getChildren().add(ligneCleValeur("Date",
                op.dateOperation == null ? "—" : Ui.formatDateTimeFull(op.dateOperation)));
        box.getChildren().add(ligneCleValeur("Caisse",
                op.caisseLibelle == null ? "—" : op.caisseLibelle));
        box.getChildren().add(ligneCleValeur("Agent",
                op.caissierNomComplet == null ? "—" : op.caissierNomComplet));
        return box;
    }

    // ==================================================================
    //  Bloc Client
    // ==================================================================

    private static boolean aDesInfosClient(OperationCaisseResponse op) {
        return (op.clientRaisonSociale != null && !op.clientRaisonSociale.isBlank())
                || (op.clientTelephone != null && !op.clientTelephone.isBlank())
                || (op.clientAdresse != null && !op.clientAdresse.isBlank());
    }

    private static Node construireBlocClient(OperationCaisseResponse op) {
        VBox box = new VBox(3);
        Label titre = new Label("CLIENT");
        titre.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        titre.setTextFill(GRAY_500);
        box.getChildren().add(titre);

        if (op.clientRaisonSociale != null && !op.clientRaisonSociale.isBlank()) {
            box.getChildren().add(ligneCleValeur("M.", op.clientRaisonSociale));
        }
        if (op.clientTelephone != null && !op.clientTelephone.isBlank()) {
            box.getChildren().add(ligneCleValeur("Téléphone", op.clientTelephone));
        }
        if (op.clientAdresse != null && !op.clientAdresse.isBlank()) {
            box.getChildren().add(ligneCleValeur("Adresse", op.clientAdresse));
        }
        return box;
    }

    // ==================================================================
    //  Détails de l'opération
    // ==================================================================

    private static Node construireDetailsOperation(OperationCaisseResponse op) {
        VBox box = new VBox(3);
        box.getChildren().add(ligneCleValeur("Type",
                op.typeOperation == null ? "—" : op.typeOperation.getLibelle()));
        box.getChildren().add(ligneCleValeur("Catégorie",
                op.categorieLibelle == null ? "—" : op.categorieLibelle));
        box.getChildren().add(ligneCleValeur("Mode règl.",
                op.modePaiement == null ? "—" : op.modePaiement.getLibelle()));
        if (op.reference != null && !op.reference.isBlank()) {
            box.getChildren().add(ligneCleValeur("Référence", op.reference));
        }
        if (op.motif != null && !op.motif.isBlank()) {
            VBox motifBox = new VBox(1);
            Label cle = new Label("Motif");
            cle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            cle.setTextFill(GRAY_500);
            Label valeur = new Label(op.motif);
            valeur.setFont(Font.font("Arial", 10));
            valeur.setTextFill(GRAY_900);
            valeur.setWrapText(true);
            valeur.setMaxWidth(LARGEUR_RECU - 36);
            motifBox.getChildren().addAll(cle, valeur);
            box.getChildren().add(motifBox);
        }
        return box;
    }

    // ==================================================================
    //  Bloc Banque (CHEQUE / VIREMENT)
    // ==================================================================

    private static boolean aDesInfosBanque(OperationCaisseResponse op) {
        return op.banqueCode != null && !op.banqueCode.isBlank();
    }

    private static Node construireBlocBanque(OperationCaisseResponse op) {
        VBox box = new VBox(3);
        Label titre = new Label("BANQUE ÉMETTRICE");
        titre.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        titre.setTextFill(GRAY_500);
        box.getChildren().add(titre);

        box.getChildren().add(ligneCleValeur("Code", op.banqueCode));
        if (op.banqueLibelle != null && !op.banqueLibelle.isBlank()) {
            box.getChildren().add(ligneCleValeur("Libellé", op.banqueLibelle));
        }
        if (op.banqueCodeEtablissement != null
                && !op.banqueCodeEtablissement.isBlank()) {
            box.getChildren().add(ligneCleValeur("Code étab.",
                    op.banqueCodeEtablissement));
        }
        if (op.banqueSiteInternet != null && !op.banqueSiteInternet.isBlank()) {
            box.getChildren().add(ligneCleValeur("Site", op.banqueSiteInternet));
        }
        return box;
    }

    // ==================================================================
    //  Montant total
    // ==================================================================

    private static Node construireMontant(OperationCaisseResponse op) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8, 0, 8, 0));
        box.setBackground(new Background(new BackgroundFill(
                Color.web("#e8f1fa"), new CornerRadii(4), Insets.EMPTY)));

        Label libelle = new Label("MONTANT TOTAL");
        libelle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        libelle.setTextFill(GRAY_700);

        Label valeur = new Label(Ui.formatMontant(op.montant));
        valeur.setFont(Font.font("Consolas", FontWeight.BOLD, 20));
        valeur.setTextFill(RTS_BLUE);

        box.getChildren().addAll(libelle, valeur);
        return box;
    }

    // ==================================================================
    //  Bandeau d'annulation
    // ==================================================================

    private static Node construireBandeauAnnulee(OperationCaisseResponse op) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 6, 0));

        Label tag = new Label("◆ OPÉRATION ANNULÉE ◆");
        tag.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        tag.setTextFill(Color.web("#dc2626"));
        box.getChildren().add(tag);

        if (op.motifAnnulation != null && !op.motifAnnulation.isBlank()) {
            Label raison = new Label("Motif : " + op.motifAnnulation);
            raison.setFont(Font.font("Arial", 9));
            raison.setTextFill(Color.web("#dc2626"));
            raison.setWrapText(true);
            raison.setMaxWidth(LARGEUR_RECU - 36);
            box.getChildren().add(raison);
        }
        return box;
    }

    // ==================================================================
    //  Pied de page
    // ==================================================================

    private static Node construirePiedDePage(OperationCaisseResponse op) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(2, 0, 0, 0));

        HBox signature = new HBox(6);
        signature.setAlignment(Pos.CENTER_LEFT);
        Label sigLabel = new Label("Signature du Caissier :");
        sigLabel.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        sigLabel.setTextFill(GRAY_700);
        Region trait = new Region();
        trait.setPrefHeight(1);
        trait.setMinHeight(1);
        trait.setMaxHeight(1);
        trait.setPrefWidth(180);
        trait.setBackground(new Background(new BackgroundFill(
                GRAY_500, CornerRadii.EMPTY, Insets.EMPTY)));
        signature.getChildren().addAll(sigLabel, trait);

        String dateImpression = (op.dateOperation == null
                ? LocalDate.now()
                : op.dateOperation.toLocalDate()).format(DATE_FR);
        Label dakar = new Label("Dakar, le " + dateImpression);
        dakar.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
        dakar.setTextFill(GRAY_700);

        Label merci = new Label("Merci de votre passage.");
        merci.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
        merci.setTextFill(GRAY_700);

        Label conserve = new Label("RTS — Conservez ce reçu comme preuve.");
        conserve.setFont(Font.font("Arial", 8));
        conserve.setTextFill(GRAY_500);

        VBox footerLabels = new VBox(1, merci, conserve);
        footerLabels.setAlignment(Pos.CENTER);
        footerLabels.setPadding(new Insets(6, 0, 0, 0));

        box.getChildren().addAll(signature, dakar, footerLabels);
        return box;
    }

    // ==================================================================
    //  Helpers de rendu
    // ==================================================================

    private static Node ligneCleValeur(String cle, String valeur) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.BASELINE_LEFT);

        Label labelCle = new Label(cle + " :");
        labelCle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        labelCle.setTextFill(GRAY_500);
        labelCle.setMinWidth(78);
        labelCle.setPrefWidth(78);

        Label labelValeur = new Label(valeur == null ? "—" : valeur);
        labelValeur.setFont(Font.font("Arial", 10));
        labelValeur.setTextFill(GRAY_900);
        labelValeur.setWrapText(true);
        labelValeur.setMaxWidth(LARGEUR_RECU - 78 - 36);

        box.getChildren().addAll(labelCle, labelValeur);
        return box;
    }

    private static Region separateur() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setBackground(new Background(new BackgroundFill(
                GRAY_300, CornerRadii.EMPTY, Insets.EMPTY)));
        VBox.setMargin(r, new Insets(8, 0, 8, 0));
        return r;
    }

    private static VBox separateurDouble() {
        Region trait1 = new Region();
        trait1.setPrefHeight(1.2);
        trait1.setBackground(new Background(new BackgroundFill(
                GRAY_900, CornerRadii.EMPTY, Insets.EMPTY)));
        Region trait2 = new Region();
        trait2.setPrefHeight(0.5);
        trait2.setBackground(new Background(new BackgroundFill(
                GRAY_900, CornerRadii.EMPTY, Insets.EMPTY)));
        VBox box = new VBox(1.5, trait1, trait2);
        VBox.setMargin(box, new Insets(8, 0, 8, 0));
        return box;
    }
}
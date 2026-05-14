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
import sn.rts.caisse.guichet.model.Dto.ParametresRecuDto;
import sn.rts.caisse.guichet.model.Dto.SectionRecu;
import sn.rts.caisse.guichet.util.ParametresRecuCache;
import sn.rts.caisse.guichet.util.Ui;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construction et impression du reçu de caisse.
 *
 * <p>Le rendu est <b>data-driven</b> : couleurs, textes statiques, ordre
 * et visibilité des sections sont lus depuis {@link ParametresRecuCache}
 * (qui interroge le backend). Si le backend est inaccessible, on retombe
 * sur les valeurs RTS d'origine pour rester opérationnel hors ligne.</p>
 */
public final class PrintRecu {

    private static final Logger log = LoggerFactory.getLogger(PrintRecu.class);

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final double LARGEUR_RECU = 380;

    // Couleurs de fallback (si backend KO ou champ null)
    private static final Color FB_PRIMAIRE = Color.web("#E30613");
    private static final Color FB_TEXTE    = Color.web("#0f172a");
    private static final Color FB_TEXTE2   = Color.web("#334155");
    private static final Color FB_MUTED    = Color.web("#64748b");
    private static final Color FB_BORDURE  = Color.web("#cbd5e1");
    private static final Color FB_DANGER   = Color.web("#dc2626");
    private static final Color FB_FOND_MNT = Color.web("#e8f1fa");

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
        double pageW = layout.getPrintableWidth();
        double pageH = layout.getPrintableHeight();

        StackPane pageBox = new StackPane(recu);
        pageBox.setAlignment(Pos.TOP_CENTER);
        pageBox.setPrefSize(pageW, pageH);
        pageBox.setMinSize(pageW, pageH);
        pageBox.setMaxSize(pageW, pageH);

        new Scene(pageBox, pageW, pageH);
        pageBox.applyCss();
        pageBox.layout();

        boolean printed = job.printPage(pageBox);
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
    //  Construction du reçu (data-driven)
    // ==================================================================

    public static Node construireRecu(OperationCaisseResponse op) {
        Ctx ctx = Ctx.charger(op);

        VBox root = new VBox(0);
        root.setPrefWidth(LARGEUR_RECU);
        root.setMaxWidth(LARGEUR_RECU);
        root.setMinWidth(LARGEUR_RECU);
        root.setBackground(new Background(new BackgroundFill(
                Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        root.setBorder(new Border(new BorderStroke(
                ctx.texte, BorderStrokeStyle.SOLID,
                new CornerRadii(4), new BorderWidths(1.5))));
        root.setPadding(new Insets(16, 18, 16, 18));

        List<SectionRecu> sections = ctx.sections();
        boolean separateurDouble = false; // après le header
        for (int i = 0; i < sections.size(); i++) {
            SectionRecu s = sections.get(i);
            if (!s.visible) continue;

            Node node = rendreSection(s.id, ctx);
            if (node == null) continue; // section conditionnelle absente

            // Séparateurs entre sections (visuels)
            if (root.getChildren().size() > 0) {
                root.getChildren().add(
                        separateurDouble ? separateurDouble(ctx) : separateur(ctx));
                separateurDouble = false;
            }
            root.getChildren().add(node);

            // Le double séparateur (gros trait) apparaît après l'en-tête.
            if ("header".equals(s.id)) {
                separateurDouble = true;
            }
        }

        return root;
    }

    /**
     * Renvoie le Node correspondant à une section, ou null si la section
     * doit rester vide (cas conditionnel : pas de client/motif/annulation).
     */
    private static Node rendreSection(String id, Ctx ctx) {
        return switch (id) {
            case "header"     -> sectionHeader(ctx);
            case "titre"      -> sectionTitre(ctx);
            case "numero"     -> sectionNumero(ctx);
            case "details"    -> sectionDetails(ctx);
            case "client"     -> ctx.aDesInfosClient() ? sectionClient(ctx) : null;
            case "montant"    -> sectionMontant(ctx);
            case "motif"      -> ctx.aDuMotif()        ? sectionMotif(ctx)  : null;
            case "annulation" -> ctx.op != null && ctx.op.annulee
                                                       ? sectionAnnulation(ctx) : null;
            case "signature"  -> sectionSignature(ctx);
            case "footer"     -> sectionFooter(ctx);
            default           -> {
                log.warn("Section inconnue ignorée : {}", id);
                yield null;
            }
        };
    }

    // ==================================================================
    //  Sections
    // ==================================================================

    private static Node sectionHeader(Ctx ctx) {
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));

        VBox logoBox = new VBox();
        logoBox.setAlignment(Pos.CENTER);
        ImageView logo = chargerLogo(ctx);
        if (logo != null) logoBox.getChildren().add(logo);
        else logoBox.getChildren().add(logoTexteFallback(ctx));
        header.getChildren().add(logoBox);

        VBox infos = new VBox(1);
        infos.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infos, javafx.scene.layout.Priority.ALWAYS);

        Label raisonSociale = new Label(orDefault(ctx.params == null ? null : ctx.params.raisonSociale,
                "SOCIÉTÉ NATIONALE DE RADIODIFFUSION TÉLÉVISION DU SÉNÉGAL"));
        raisonSociale.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        raisonSociale.setTextFill(ctx.primaire);
        raisonSociale.setWrapText(true);
        raisonSociale.setMaxWidth(LARGEUR_RECU - 80);

        infos.getChildren().add(raisonSociale);

        ajouterLigneInfo(infos, ctx, ctx.params == null ? null : ctx.params.sousTitreEntete);
        ajouterLigneInfo(infos, ctx, ctx.params == null ? "Créée par la loi n° 92-02 du 06 janvier 1992"
                : ctx.params.ligneLegale);
        ajouterLigneInfo(infos, ctx, ctx.params == null ? "Capital : 7 milliards FCFA"
                : ctx.params.capital);
        ajouterLigneInfo(infos, ctx, ctx.params == null ? "Triangle Sud"
                : ctx.params.adresse);
        ajouterLigneInfo(infos, ctx, ctx.params == null ? "Tél. (221) 33 849 12 12"
                : ctx.params.telephone);
        ajouterLigneInfo(infos, ctx, ctx.params == null ? "B.P. 1765 — DAKAR"
                : ctx.params.boitePostale);

        String ninea = ctx.params == null ? "NINEA : 2059782 2G3" : ctx.params.ninea;
        if (ninea != null && !ninea.isBlank()) {
            Label l = new Label(ninea);
            l.setFont(Font.font("Arial", FontWeight.BOLD, 8));
            l.setTextFill(ctx.texte);
            infos.getChildren().add(l);
        }

        header.getChildren().add(infos);
        return header;
    }

    private static void ajouterLigneInfo(VBox box, Ctx ctx, String texte) {
        if (texte == null || texte.isBlank()) return;
        Label l = new Label(texte);
        l.setFont(Font.font("Arial", 8));
        l.setTextFill(ctx.texte2);
        box.getChildren().add(l);
    }

    private static ImageView chargerLogo(Ctx ctx) {
        // 1) Logo binaire fourni par le backend (cache)
        if (ctx.logoImage != null && ctx.logoImage.length > 0) {
            try {
                Image img = new Image(new ByteArrayInputStream(ctx.logoImage),
                        56, 56, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(56);
                iv.setFitHeight(56);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                return iv;
            } catch (Exception e) {
                log.warn("Logo backend illisible, fallback ressource locale : {}",
                        e.getMessage());
            }
        }

        // 2) Ressource locale (ancien comportement)
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
                    log.warn("Logo trouvé à {} mais illisible : {}", chemin, e.getMessage());
                }
            }
        }
        return null;
    }

    /** Fallback texte type pastille rouge "RTS" si aucune image. */
    private static Node logoTexteFallback(Ctx ctx) {
        String texte = ctx.params != null && ctx.params.logoTexte != null
                && !ctx.params.logoTexte.isBlank()
                ? ctx.params.logoTexte : "RTS";
        StackPane pastille = new StackPane();
        pastille.setPrefSize(56, 56);
        pastille.setMinSize(56, 56);
        pastille.setMaxSize(56, 56);
        pastille.setBackground(new Background(new BackgroundFill(
                ctx.primaire, new CornerRadii(6), Insets.EMPTY)));
        Label l = new Label(texte);
        l.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        l.setTextFill(Color.WHITE);
        pastille.getChildren().add(l);
        return pastille;
    }

    private static Node sectionTitre(Ctx ctx) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 0, 0));
        Label l = new Label("REÇU");
        l.setFont(Font.font("Arial", FontWeight.BOLD, ctx.tailleTitre));
        l.setTextFill(ctx.texte);
        box.getChildren().add(l);
        return box;
    }

    private static Node sectionNumero(Ctx ctx) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(0, 0, 6, 0));
        String numero = ctx.op != null && ctx.op.numeroRecu != null
                ? ctx.op.numeroRecu : "RTS-AAAA-XXX-00000";
        Label l = new Label("N°  " + numero);
        l.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        l.setTextFill(ctx.primaire);
        box.getChildren().add(l);
        return box;
    }

    private static Node sectionDetails(Ctx ctx) {
        VBox box = new VBox(3);
        if (ctx.op != null) {
            box.getChildren().add(ligneCleValeur(ctx, "Date",
                    ctx.op.dateOperation == null ? "—" : Ui.formatDateTimeFull(ctx.op.dateOperation)));
            box.getChildren().add(ligneCleValeur(ctx, "Caisse",
                    ctx.op.caisseLibelle == null ? "—" : ctx.op.caisseLibelle));
            box.getChildren().add(ligneCleValeur(ctx, "Agent",
                    ctx.op.caissierNomComplet == null ? "—" : ctx.op.caissierNomComplet));
            box.getChildren().add(ligneCleValeur(ctx, "Type",
                    ctx.op.typeOperation == null ? "—" : ctx.op.typeOperation.getLibelle()));
            box.getChildren().add(ligneCleValeur(ctx, "Catégorie",
                    ctx.op.categorieLibelle == null ? "—" : ctx.op.categorieLibelle));
            box.getChildren().add(ligneCleValeur(ctx, "Mode régl.",
                    ctx.op.modePaiement == null ? "—" : ctx.op.modePaiement.getLibelle()));
            if (ctx.op.reference != null && !ctx.op.reference.isBlank()) {
                box.getChildren().add(ligneCleValeur(ctx, "Référence", ctx.op.reference));
            }
            if (aDesInfosBanque(ctx.op)) {
                box.getChildren().add(blocBanque(ctx));
            }
        }
        return box;
    }

    private static Node sectionClient(Ctx ctx) {
        VBox box = new VBox(3);
        Label titre = new Label("CLIENT");
        titre.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        titre.setTextFill(ctx.muted);
        box.getChildren().add(titre);

        if (ctx.op == null) return box;
        if (ctx.op.clientRaisonSociale != null && !ctx.op.clientRaisonSociale.isBlank()) {
            box.getChildren().add(ligneCleValeur(ctx, "M.", ctx.op.clientRaisonSociale));
        }
        if (ctx.op.clientTelephone != null && !ctx.op.clientTelephone.isBlank()) {
            box.getChildren().add(ligneCleValeur(ctx, "Téléphone", ctx.op.clientTelephone));
        }
        if (ctx.op.clientAdresse != null && !ctx.op.clientAdresse.isBlank()) {
            box.getChildren().add(ligneCleValeur(ctx, "Adresse", ctx.op.clientAdresse));
        }
        return box;
    }

    private static Node blocBanque(Ctx ctx) {
        VBox box = new VBox(3);
        box.setPadding(new Insets(4, 0, 0, 0));
        Label titre = new Label("BANQUE ÉMETTRICE");
        titre.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        titre.setTextFill(ctx.muted);
        box.getChildren().add(titre);

        box.getChildren().add(ligneCleValeur(ctx, "Code", ctx.op.banqueCode));
        if (ctx.op.banqueLibelle != null && !ctx.op.banqueLibelle.isBlank()) {
            box.getChildren().add(ligneCleValeur(ctx, "Libellé", ctx.op.banqueLibelle));
        }
        if (ctx.op.banqueCodeEtablissement != null
                && !ctx.op.banqueCodeEtablissement.isBlank()) {
            box.getChildren().add(ligneCleValeur(ctx, "Code étab.",
                    ctx.op.banqueCodeEtablissement));
        }
        if (ctx.op.banqueSiteInternet != null && !ctx.op.banqueSiteInternet.isBlank()) {
            box.getChildren().add(ligneCleValeur(ctx, "Site", ctx.op.banqueSiteInternet));
        }
        return box;
    }

    private static Node sectionMontant(Ctx ctx) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8, 0, 8, 0));
        box.setBackground(new Background(new BackgroundFill(
                ctx.fondMontant, new CornerRadii(4), Insets.EMPTY)));

        Label libelle = new Label("MONTANT TOTAL");
        libelle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        libelle.setTextFill(ctx.texte2);

        String montantTxt = ctx.op != null
                ? Ui.formatMontant(ctx.op.montant) : "0";
        Label valeur = new Label(montantTxt);
        valeur.setFont(Font.font("Consolas", FontWeight.BOLD, ctx.tailleMontant));
        valeur.setTextFill(ctx.primaire);

        box.getChildren().addAll(libelle, valeur);
        return box;
    }

    private static Node sectionMotif(Ctx ctx) {
        VBox box = new VBox(2);
        Label cle = new Label("MOTIF");
        cle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        cle.setTextFill(ctx.muted);
        Label valeur = new Label(ctx.op.motif);
        valeur.setFont(Font.font("Arial", 10));
        valeur.setTextFill(ctx.texte);
        valeur.setWrapText(true);
        valeur.setMaxWidth(LARGEUR_RECU - 36);
        box.getChildren().addAll(cle, valeur);
        return box;
    }

    private static Node sectionAnnulation(Ctx ctx) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 6, 0));

        Label tag = new Label("◆ OPÉRATION ANNULÉE ◆");
        tag.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        tag.setTextFill(ctx.danger);
        box.getChildren().add(tag);

        if (ctx.op.motifAnnulation != null && !ctx.op.motifAnnulation.isBlank()) {
            Label raison = new Label("Motif : " + ctx.op.motifAnnulation);
            raison.setFont(Font.font("Arial", 9));
            raison.setTextFill(ctx.danger);
            raison.setWrapText(true);
            raison.setMaxWidth(LARGEUR_RECU - 36);
            box.getChildren().add(raison);
        }
        return box;
    }

    private static Node sectionSignature(Ctx ctx) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(2, 0, 0, 0));

        HBox signature = new HBox(6);
        signature.setAlignment(Pos.CENTER_LEFT);
        Label sigLabel = new Label("Signature du Caissier :");
        sigLabel.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        sigLabel.setTextFill(ctx.texte2);
        Region trait = new Region();
        trait.setPrefHeight(1);
        trait.setMinHeight(1);
        trait.setMaxHeight(1);
        trait.setPrefWidth(180);
        trait.setBackground(new Background(new BackgroundFill(
                ctx.muted, CornerRadii.EMPTY, Insets.EMPTY)));
        signature.getChildren().addAll(sigLabel, trait);

        String dateImpression = (ctx.op == null || ctx.op.dateOperation == null
                ? LocalDate.now()
                : ctx.op.dateOperation.toLocalDate()).format(DATE_FR);
        String ville = ctx.params != null && ctx.params.villeSignature != null
                && !ctx.params.villeSignature.isBlank()
                ? ctx.params.villeSignature : "Dakar";
        Label dakar = new Label(ville + ", le " + dateImpression);
        dakar.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
        dakar.setTextFill(ctx.texte2);

        box.getChildren().addAll(signature, dakar);
        return box;
    }

    private static Node sectionFooter(Ctx ctx) {
        VBox box = new VBox(1);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 0, 0));

        String l1 = ctx.params != null && ctx.params.footerLigne1 != null
                && !ctx.params.footerLigne1.isBlank()
                ? ctx.params.footerLigne1 : "Merci de votre passage.";
        String l2 = ctx.params != null && ctx.params.footerLigne2 != null
                && !ctx.params.footerLigne2.isBlank()
                ? ctx.params.footerLigne2 : "RTS — Conservez ce reçu comme preuve.";

        Label merci = new Label(l1);
        merci.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
        merci.setTextFill(ctx.texte2);

        Label conserve = new Label(l2);
        conserve.setFont(Font.font("Arial", 8));
        conserve.setTextFill(ctx.muted);

        box.getChildren().addAll(merci, conserve);
        return box;
    }

    // ==================================================================
    //  Helpers de rendu
    // ==================================================================

    private static Node ligneCleValeur(Ctx ctx, String cle, String valeur) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.BASELINE_LEFT);

        Label labelCle = new Label(cle + " :");
        labelCle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        labelCle.setTextFill(ctx.muted);
        labelCle.setMinWidth(78);
        labelCle.setPrefWidth(78);

        Label labelValeur = new Label(valeur == null ? "—" : valeur);
        labelValeur.setFont(Font.font("Arial", 10));
        labelValeur.setTextFill(ctx.texte);
        labelValeur.setWrapText(true);
        labelValeur.setMaxWidth(LARGEUR_RECU - 78 - 36);

        box.getChildren().addAll(labelCle, labelValeur);
        return box;
    }

    private static Region separateur(Ctx ctx) {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setBackground(new Background(new BackgroundFill(
                ctx.bordure, CornerRadii.EMPTY, Insets.EMPTY)));
        VBox.setMargin(r, new Insets(8, 0, 8, 0));
        return r;
    }

    private static VBox separateurDouble(Ctx ctx) {
        Region trait1 = new Region();
        trait1.setPrefHeight(1.2);
        trait1.setBackground(new Background(new BackgroundFill(
                ctx.texte, CornerRadii.EMPTY, Insets.EMPTY)));
        Region trait2 = new Region();
        trait2.setPrefHeight(0.5);
        trait2.setBackground(new Background(new BackgroundFill(
                ctx.texte, CornerRadii.EMPTY, Insets.EMPTY)));
        VBox box = new VBox(1.5, trait1, trait2);
        VBox.setMargin(box, new Insets(8, 0, 8, 0));
        return box;
    }

    private static boolean aDesInfosBanque(OperationCaisseResponse op) {
        return op != null && op.banqueCode != null && !op.banqueCode.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    // ==================================================================
    //  Contexte de rendu : agrège op + params + couleurs + tailles résolues
    // ==================================================================

    private static final class Ctx {
        final OperationCaisseResponse op;
        final ParametresRecuDto params;
        final byte[] logoImage;

        // Couleurs résolues (jamais null)
        final Color primaire, texte, texte2, muted, bordure, danger, fondMontant;

        // Tailles résolues
        final int tailleTitre, tailleMontant;

        private Ctx(OperationCaisseResponse op, ParametresRecuDto params, byte[] logo,
                    Color primaire, Color texte, Color texte2, Color muted,
                    Color bordure, Color danger, Color fondMontant,
                    int tailleTitre, int tailleMontant) {
            this.op = op;
            this.params = params;
            this.logoImage = logo;
            this.primaire = primaire;
            this.texte = texte;
            this.texte2 = texte2;
            this.muted = muted;
            this.bordure = bordure;
            this.danger = danger;
            this.fondMontant = fondMontant;
            this.tailleTitre = tailleTitre;
            this.tailleMontant = tailleMontant;
        }

        static Ctx charger(OperationCaisseResponse op) {
            ParametresRecuDto p = null;
            byte[] logo = null;
            try {
                p = ParametresRecuCache.getInstance().obtenir();
                logo = ParametresRecuCache.getInstance().obtenirLogo();
            } catch (Exception e) {
                log.warn("Paramètres reçu indisponibles, fallback défaut : {}",
                        e.getMessage());
            }

            return new Ctx(op, p, logo,
                    hex(p == null ? null : p.couleurPrimaire,        FB_PRIMAIRE),
                    hex(p == null ? null : p.couleurTexte,           FB_TEXTE),
                    hex(p == null ? null : p.couleurTexteSecondaire, FB_TEXTE2),
                    hex(p == null ? null : p.couleurTexteSecondaire, FB_MUTED),
                    FB_BORDURE,
                    hex(p == null ? null : p.couleurDanger,          FB_DANGER),
                    hex(p == null ? null : p.couleurFondMontant,     FB_FOND_MNT),
                    nz(p == null ? null : p.tailleTitre,             14),
                    nz(p == null ? null : p.tailleMontant,           20));
        }

        boolean aDesInfosClient() {
            return op != null
                    && ((op.clientRaisonSociale != null && !op.clientRaisonSociale.isBlank())
                    || (op.clientTelephone != null && !op.clientTelephone.isBlank())
                    || (op.clientAdresse != null && !op.clientAdresse.isBlank()));
        }

        boolean aDuMotif() {
            return op != null && op.motif != null && !op.motif.isBlank();
        }

        List<SectionRecu> sections() {
            if (params != null && params.sections != null && !params.sections.isEmpty()) {
                return params.sections;
            }
            // Ordre par défaut (cohérent avec le backend)
            String[] ids = {"header","titre","numero","details","client","montant",
                            "motif","annulation","signature","footer"};
            List<SectionRecu> list = new ArrayList<>();
            for (String id : ids) {
                SectionRecu s = new SectionRecu();
                s.id = id;
                s.visible = true;
                list.add(s);
            }
            return list;
        }

        private static Color hex(String hexValue, Color fallback) {
            if (hexValue == null || hexValue.isBlank()) return fallback;
            try {
                return Color.web(hexValue);
            } catch (Exception e) {
                return fallback;
            }
        }

        private static int nz(Integer v, int fallback) {
            return v != null && v > 0 ? v : fallback;
        }
    }
}

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

    /** Date + heure : utilise pour la ligne "Diffusion" sur le recu. */
    private static final DateTimeFormatter DATE_HEURE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    /**
     * Largeur du recu pour l'apercu et l'impression desktop. Passe de 380 a
     * 520 px pour s'aligner sur le format A5 du PDF backend (148 mm de large)
     * et eviter que les libelles longs (categorie, banque) ne soient tronques.
     */
    private static final double LARGEUR_RECU = 520;

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

            // Le double separateur (gros trait) apparait apres la derniere
            // rubrique d'en-tete (NINEA par defaut). Si l'admin reorganise,
            // le trait suit logiquement la rubrique NINEA ou disparait si
            // celle-ci est masquee.
            if ("ninea".equals(s.id)) {
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
            // -------- En-tete societe (8 rubriques granulaires) --------
            case "logo"              -> sectionLogo(ctx);
            case "raison_sociale"    -> ligneCentree(ctx, ctx.params != null ? ctx.params.raisonSociale : null,
                                                     11, FontWeight.BOLD, ctx.primaire);
            case "ligne_legale"      -> ligneCentree(ctx, ctx.params != null ? ctx.params.ligneLegale   : null,
                                                     9,  FontWeight.NORMAL, ctx.texte2);
            case "capital"           -> ligneCentree(ctx, ctx.params != null ? ctx.params.capital       : null,
                                                     9,  FontWeight.NORMAL, ctx.texte2);
            case "adresse_societe"   -> ligneCentree(ctx, ctx.params != null ? ctx.params.adresse       : null,
                                                     9,  FontWeight.NORMAL, ctx.texte2);
            case "telephone_societe" -> ligneCentree(ctx, ctx.params != null ? ctx.params.telephone     : null,
                                                     9,  FontWeight.NORMAL, ctx.texte2);
            case "boite_postale"     -> ligneCentree(ctx, ctx.params != null ? ctx.params.boitePostale  : null,
                                                     9,  FontWeight.NORMAL, ctx.texte2);
            case "ninea"             -> ligneCentree(ctx, ctx.params != null ? ctx.params.ninea         : null,
                                                     9,  FontWeight.BOLD, ctx.texte);
            // -------- Titre + numero --------
            case "titre_recu"        -> sectionTitre(ctx);
            case "numero_recu"       -> sectionNumero(ctx);
            // -------- Details operation (8 rubriques granulaires) --------
            case "date_operation"    -> ligneCleValeur(ctx, "Date",
                                            ctx.op != null && ctx.op.dateOperation != null
                                                ? ctx.op.dateOperation.format(DATE_HEURE_FR)
                                                : "—");
            case "caisse"            -> ligneCleValeur(ctx, "Caisse",
                                            ctx.op != null ? ctx.op.caisseLibelle : "—");
            case "agent"             -> ligneCleValeur(ctx, "Agent",
                                            ctx.op != null ? ctx.op.caissierNomComplet : "—");
            case "type_operation"    -> ligneCleValeur(ctx, "Type",
                                            ctx.op != null && ctx.op.typeOperation != null
                                                ? ctx.op.typeOperation.getLibelle() : "—");
            case "categorie"         -> ligneCleValeur(ctx, "Catégorie",
                                            ctx.op != null ? ctx.op.categorieLibelle : "—");
            case "mode_paiement"     -> ligneCleValeur(ctx, "Mode régl.",
                                            ctx.op != null && ctx.op.modePaiement != null
                                                ? ctx.op.modePaiement.getLibelle() : "—");
            case "reference"         -> (ctx.op != null && ctx.op.reference != null && !ctx.op.reference.isBlank())
                                            ? ligneCleValeur(ctx, "Référence", ctx.op.reference) : null;
            case "diffusion"         -> ligneCleValeur(ctx, "Diffusion",
                                            ctx.op != null && ctx.op.dateDiffusion != null
                                                ? ctx.op.dateDiffusion.format(DATE_HEURE_FR)
                                                : "—");
            // -------- Banque (bloc unitaire) --------
            case "banque"            -> aDesInfosBanque(ctx.op) ? blocBanque(ctx) : null;
            // -------- Client (4 rubriques granulaires) --------
            case "client_raison"     -> sectionClientChamp(ctx, "M.",        c -> c != null ? c.raisonSociale     : null);
            case "client_telephone"  -> sectionClientChamp(ctx, "Téléphone", c -> c != null ? c.telephone         : null);
            case "client_adresse"    -> sectionClientChamp(ctx, "Adresse",   c -> c != null ? c.adresse           : null);
            case "client_ninea"      -> sectionClientChamp(ctx, "NINEA/RCCM",c -> c != null ? c.identifiantFiscal : null);
            // -------- Montant (bloc visuel unitaire) --------
            case "montant"           -> sectionMontant(ctx);
            // -------- Motif, annulation, signature --------
            case "motif"             -> ctx.aDuMotif() ? sectionMotif(ctx) : null;
            case "annulation"        -> ctx.op != null && ctx.op.annulee ? sectionAnnulation(ctx) : null;
            case "signature"         -> sectionSignature(ctx);
            // -------- Footer (2 rubriques granulaires) --------
            case "footer_ligne1"     -> ligneCentree(ctx,
                                            blankIfNull(ctx.params != null ? ctx.params.footerLigne1 : null,
                                                    "Merci de votre passage."),
                                            8, FontWeight.NORMAL, ctx.texte);
            case "footer_ligne2"     -> ligneCentree(ctx,
                                            blankIfNull(ctx.params != null ? ctx.params.footerLigne2 : null,
                                                    "RTS - Conservez ce recu."),
                                            7, FontWeight.NORMAL, ctx.texte2);
            default                  -> {
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
            // Heure de diffusion du produit a l'antenne (spot, sponsoring...).
            // TOUJOURS affichee : on imprime "—" si non renseignee, pour que la
            // rubrique soit visible sur tous les recus.
            String diffusion = ctx.op.dateDiffusion != null
                    ? ctx.op.dateDiffusion.format(DATE_HEURE_FR)
                    : "—";
            box.getChildren().add(ligneCleValeur(ctx, "Diffusion", diffusion));
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
        box.setPadding(new Insets(8, 12, 8, 12));
        // Bloc montant : fond blanc, look plat (on ignore volontairement
        // ctx.fondMontant pour ne pas reintroduire un encadre colore).
        box.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(4), Insets.EMPTY)));

        java.math.BigDecimal montant = ctx.op != null && ctx.op.montant != null
                ? ctx.op.montant : java.math.BigDecimal.ZERO;
        java.math.BigDecimal timbre  = ctx.op != null && ctx.op.timbre != null
                ? ctx.op.timbre : java.math.BigDecimal.ZERO;
        java.math.BigDecimal ttc     = ctx.op != null && ctx.op.montantTtc != null
                ? ctx.op.montantTtc : montant.add(timbre);

        boolean afficheDetail = timbre.signum() > 0;

        if (afficheDetail) {
            // Lignes Montant HT + Timbre alignées.
            // Ui.formatMontant() inclut deja " FCFA", pas besoin de l'ajouter
            // (sinon doublon "FCFA FCFA" sur le recu).
            box.getChildren().add(ligneMontantInline(ctx, "Montant HT",
                    Ui.formatMontant(montant)));
            box.getChildren().add(ligneMontantInline(ctx, "Timbre",
                    Ui.formatMontant(timbre)));

            // Séparateur
            Region sep = new Region();
            sep.setPrefHeight(1);
            sep.setMaxHeight(1);
            sep.setPrefWidth(180);
            sep.setBackground(new Background(new BackgroundFill(
                    ctx.muted, CornerRadii.EMPTY, Insets.EMPTY)));
            VBox.setMargin(sep, new Insets(4, 0, 4, 0));
            box.getChildren().add(sep);

            Label libelleTtc = new Label("MONTANT TTC");
            libelleTtc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            libelleTtc.setTextFill(ctx.texte2);
            box.getChildren().add(libelleTtc);

            Label valeurTtc = new Label(Ui.formatMontant(ttc));
            valeurTtc.setFont(Font.font("Consolas", FontWeight.BOLD, ctx.tailleMontant));
            valeurTtc.setTextFill(ctx.primaire);
            box.getChildren().add(valeurTtc);
        } else {
            Label libelle = new Label("MONTANT TOTAL");
            libelle.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            libelle.setTextFill(ctx.texte2);
            box.getChildren().add(libelle);

            Label valeur = new Label(Ui.formatMontant(montant));
            valeur.setFont(Font.font("Consolas", FontWeight.BOLD, ctx.tailleMontant));
            valeur.setTextFill(ctx.primaire);
            box.getChildren().add(valeur);
        }
        return box;
    }

    /** Ligne « Libellé : Valeur » centrée, utilisée dans le bloc Montant TTC. */
    private static Node ligneMontantInline(Ctx ctx, String libelle, String valeur) {
        HBox h = new HBox(8);
        h.setAlignment(Pos.CENTER);
        Label l = new Label(libelle);
        l.setFont(Font.font("Arial", 9));
        l.setTextFill(ctx.texte2);
        Label v = new Label(valeur);
        v.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        v.setTextFill(ctx.texte);
        h.getChildren().addAll(l, v);
        return h;
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

    /** Remplace null/blanc par {@code fallback}, retourne value sinon. */
    private static String blankIfNull(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    // ==================================================================
    //  Helpers granulaires (rubrique unitaires de l'en-tete et du footer)
    // ==================================================================

    /** Logo standalone centre — utilise par la rubrique "logo". */
    private static Node sectionLogo(Ctx ctx) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        ImageView logo = chargerLogo(ctx);
        if (logo != null) {
            box.getChildren().add(logo);
        } else {
            box.getChildren().add(logoTexteFallback(ctx));
        }
        return box;
    }

    /**
     * Ligne de texte centree (utilisee pour chaque info societe granulaire
     * et pour les deux lignes de pied de page). Retourne null si texte vide
     * pour ne pas afficher de ligne creuse.
     */
    private static Node ligneCentree(Ctx ctx, String texte, double tailleFont,
                                      FontWeight weight,
                                      javafx.scene.paint.Color couleur) {
        if (texte == null || texte.isBlank()) return null;
        Label label = new Label(texte);
        label.setFont(Font.font("Arial", weight, tailleFont));
        label.setTextFill(couleur);
        label.setWrapText(true);
        HBox box = new HBox(label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Une rubrique client granulaire : "M. : <raison sociale>",
     * "Telephone : <tel>", etc. Retourne null si l'op n'a pas de client
     * ou si le champ est vide (rubrique conditionnelle silencieuse).
     */
    private static Node sectionClientChamp(Ctx ctx, String label,
            java.util.function.Function<sn.rts.caisse.guichet.model.Dto.ClientDTO, String> getter) {
        if (ctx.op == null) {
            // apercu : on affiche un exemple
            return ligneCleValeur(ctx, label, "Client exemple");
        }
        // Le DTO de reponse expose les champs client a plat (pas d'objet
        // Client embarque). On utilise un mini-DTO synthetique.
        sn.rts.caisse.guichet.model.Dto.ClientDTO c =
                synthClientFromOp(ctx.op);
        if (c == null) return null;
        String valeur = getter.apply(c);
        if (valeur == null || valeur.isBlank()) return null;
        return ligneCleValeur(ctx, label, valeur);
    }

    /**
     * Construit un objet {@link sn.rts.caisse.guichet.model.Dto.ClientDTO}
     * a partir des champs a plat de l'OperationCaisseResponse, ou retourne
     * null si aucun client n'est associe a l'operation.
     */
    private static sn.rts.caisse.guichet.model.Dto.ClientDTO synthClientFromOp(
            OperationCaisseResponse op) {
        if (op.clientId == null && (op.clientRaisonSociale == null
                || op.clientRaisonSociale.isBlank())) {
            return null;
        }
        sn.rts.caisse.guichet.model.Dto.ClientDTO c =
                new sn.rts.caisse.guichet.model.Dto.ClientDTO();
        c.id = op.clientId;
        c.raisonSociale     = op.clientRaisonSociale;
        c.telephone         = op.clientTelephone;
        c.adresse           = op.clientAdresse;
        c.identifiantFiscal = op.clientIdentifiantFiscal;
        return c;
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

        /** IDs de l'ancien format coarse (avant la granularite max).
         *  Si l'un d'eux apparait, on considere le layout obsolete et on
         *  retombe sur la config granulaire par defaut. Migration identique
         *  cote backend (RecuPdfService) et frontend web. */
        private static final java.util.Set<String> ANCIENS_IDS = java.util.Set.of(
                "header", "details", "client", "footer", "numero", "titre");

        List<SectionRecu> sections() {
            if (params != null && params.sections != null && !params.sections.isEmpty()) {
                // Detection d'un ancien layout : si un seul ancien ID est
                // present, on ignore le layout sauvegarde et on retombe sur
                // les 29 rubriques granulaires. Evite que le recu apparaisse
                // vide quand l'admin n'a pas encore re-enregistre les
                // parametres depuis la mise a jour.
                boolean obsolete = params.sections.stream()
                        .anyMatch(s -> ANCIENS_IDS.contains(s.id));
                if (!obsolete) {
                    return params.sections;
                }
                log.info("Layout recu obsolete detecte cote guichet, "
                        + "fallback sur la config granulaire par defaut.");
            }
            // Ordre par defaut : 29 rubriques granulaires (coherent avec
            // backend RecuPdfService.sectionsDefaut()).
            String[] ids = {
                    // En-tete societe (8)
                    "logo","raison_sociale","ligne_legale","capital",
                    "adresse_societe","telephone_societe","boite_postale","ninea",
                    // Titre + numero
                    "titre_recu","numero_recu",
                    // Details operation (8)
                    "date_operation","caisse","agent","type_operation",
                    "categorie","mode_paiement","reference","diffusion",
                    // Banque
                    "banque",
                    // Client (4)
                    "client_raison","client_telephone","client_adresse","client_ninea",
                    // Montant
                    "montant",
                    // Motif, annulation, signature
                    "motif","annulation","signature",
                    // Footer (2)
                    "footer_ligne1","footer_ligne2"
            };
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

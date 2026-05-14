package sn.rts.caisse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.ParametresRecuDto;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.ParametresRecu;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Génère le reçu PDF d'une opération de caisse.
 *
 * <p>Le document produit est un <b>ticket A6</b> (~105 × 148 mm). Les couleurs,
 * tailles, textes statiques et ordre des sections sont lus dynamiquement
 * depuis {@link ParametresRecuService} (table {@code parametres_recu}), ce
 * qui permet à un ADMIN de personnaliser le rendu via l'interface web sans
 * redéploiement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecuPdfService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm:ss");

    private static final DateTimeFormatter DATE_COURTE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final NumberFormat MONTANT_FMT;
    static {
        MONTANT_FMT = NumberFormat.getNumberInstance(Locale.FRANCE);
        MONTANT_FMT.setMinimumFractionDigits(0);
        MONTANT_FMT.setMaximumFractionDigits(0);
    }

    private final OperationCaisseRepository operationRepository;
    private final ParametresRecuService parametresService;
    private final ObjectMapper objectMapper;

    // ==================================================================
    //  ENTRÉE PRINCIPALE
    // ==================================================================

    public byte[] genererRecu(Long operationId) {
        OperationCaisse op = operationRepository.findById(operationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Opération", operationId));
        return genererPdf(op, parametresService.obtenirEntite());
    }

    /**
     * Génère un PDF d'aperçu avec une opération fictive et les paramètres
     * courants. Utilisé par la page « Paramètres du reçu » pour donner un
     * rendu en temps réel sans nécessiter d'opération existante.
     */
    public byte[] genererApercu() {
        return genererPdf(null, parametresService.obtenirEntite());
    }

    private byte[] genererPdf(OperationCaisse op, ParametresRecu params) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A6, 20, 20, 20, 20);

        // Couleurs et tailles depuis les paramètres (avec fallback)
        Color cPrimaire = hex(params.getCouleurPrimaire(), new Color(227, 6, 19));
        Color cAccent   = hex(params.getCouleurAccent(),   new Color(26, 26, 26));
        Color cTexte    = hex(params.getCouleurTexte(),    new Color(33, 33, 33));
        Color cTexteSec = hex(params.getCouleurTexteSecondaire(), new Color(158, 158, 158));
        Color cSuccess  = hex(params.getCouleurSuccess(),  new Color(46, 125, 50));
        Color cDanger   = hex(params.getCouleurDanger(),   new Color(198, 40, 40));
        Color cFondMnt  = hex(params.getCouleurFondMontant(), new Color(251, 229, 231));

        int tTitre   = nz(params.getTailleTitre(),   14);
        int tEntete  = nz(params.getTailleEntete(),  16);
        int tCorps   = nz(params.getTailleCorps(),    9);
        int tMontant = nz(params.getTailleMontant(), 20);
        int tFooter  = nz(params.getTailleFooter(),   7);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            List<ParametresRecuDto.Section> sections = sectionsOrdonnees(params);

            for (ParametresRecuDto.Section s : sections) {
                if (!s.visible()) continue;
                switch (s.id()) {
                    case "header"     -> ecrireEnTete(document, params, cPrimaire, cTexte, cTexteSec, tEntete);
                    case "titre"      -> ecrireTitreRecu(document, op, cPrimaire, tTitre);
                    case "numero"     -> ecrireNumeroEtDate(document, op, cPrimaire, cTexteSec, tCorps);
                    case "details"    -> ecrireDetails(document, op, cTexte, cTexteSec, tCorps);
                    case "client"     -> ecrireClient(document, op, cTexte, cTexteSec, tCorps);
                    case "montant"    -> ecrireMontant(document, op, cTexte, cFondMnt, cSuccess, cDanger, tCorps, tMontant);
                    case "motif"      -> ecrireMotif(document, op, cTexte, cTexteSec, tCorps);
                    case "annulation" -> ecrireAnnulation(document, op, cDanger, tCorps);
                    case "signature"  -> ecrireSignature(document, params, cTexte, cTexteSec, tCorps);
                    case "footer"     -> ecrirePiedPage(document, params, cAccent, cTexte, cTexteSec, tFooter);
                    default           -> log.warn("Section inconnue ignorée : {}", s.id());
                }
            }

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Échec de la génération du PDF", e);
            throw new RuntimeException("Impossible de générer le reçu PDF : " + e.getMessage(), e);
        }
    }

    // ==================================================================
    //  SECTIONS
    // ==================================================================

    private void ecrireEnTete(Document document, ParametresRecu p,
                              Color cPrim, Color cTexte, Color cTexteSec, int tEntete) throws Exception {
        PdfPTable header = new PdfPTable(new float[]{25, 75});
        header.setWidthPercentage(100);

        // Cellule logo : image si présente, sinon texte
        PdfPCell cLogo = new PdfPCell();
        cLogo.setBorder(PdfPCell.NO_BORDER);
        cLogo.setPadding(4);
        cLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);

        boolean logoImageAjoute = false;
        if (p.getLogoImage() != null && p.getLogoImage().length > 0) {
            try {
                Image img = Image.getInstance(p.getLogoImage());
                img.scaleToFit(60, 60);
                img.setAlignment(Image.ALIGN_CENTER);
                cLogo.addElement(img);
                logoImageAjoute = true;
            } catch (Exception ex) {
                log.warn("Logo image illisible ({}), fallback sur le texte.",
                        ex.getMessage());
            }
        }
        if (!logoImageAjoute) {
            cLogo.setBackgroundColor(cPrim);
            cLogo.setPadding(10);
            Paragraph logo = new Paragraph(blankIfNull(p.getLogoTexte(), "RTS"),
                    font(tEntete + 4, Font.BOLD, Color.WHITE));
            logo.setAlignment(Element.ALIGN_CENTER);
            cLogo.addElement(logo);
        }
        header.addCell(cLogo);

        // Cellule infos
        PdfPCell cInfos = new PdfPCell();
        cInfos.setBorder(PdfPCell.NO_BORDER);
        cInfos.setPaddingLeft(8);
        addParag(cInfos, p.getRaisonSociale(),    font(8.5f, Font.BOLD, cPrim));
        addParag(cInfos, p.getLigneLegale(),      font(7,    Font.NORMAL, cTexteSec));
        addParag(cInfos, p.getCapital(),          font(7,    Font.NORMAL, cTexteSec));
        addParag(cInfos, p.getAdresse(),          font(7,    Font.NORMAL, cTexteSec));
        addParag(cInfos, p.getTelephone(),        font(7,    Font.NORMAL, cTexteSec));
        addParag(cInfos, p.getBoitePostale(),     font(7,    Font.NORMAL, cTexteSec));
        addParag(cInfos, p.getNinea(),            font(7,    Font.BOLD,   cTexte));
        header.addCell(cInfos);

        document.add(header);
        saut(document, 6);
    }

    private void ecrireTitreRecu(Document document, OperationCaisse op,
                                 Color cPrim, int tTitre) throws Exception {
        Paragraph titre = new Paragraph("REÇU", font(tTitre, Font.BOLD, Color.BLACK));
        titre.setAlignment(Element.ALIGN_CENTER);
        document.add(titre);
    }

    private void ecrireNumeroEtDate(Document document, OperationCaisse op,
                                    Color cPrim, Color cTexteSec, int tCorps) throws Exception {
        String numero = op != null ? op.getNumeroRecu() : "RTS-AAAA-XXX-00000";
        Paragraph p = new Paragraph();
        p.add(new Chunk("N°   ", font(tCorps + 2, Font.BOLD, Color.BLACK)));
        p.add(new Chunk(numero, font(tCorps + 4, Font.BOLD, cPrim)));
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
        saut(document, 6);
    }

    private void ecrireDetails(Document document, OperationCaisse op,
                               Color cTexte, Color cTexteSec, int tCorps) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{35, 65});
        table.setWidthPercentage(100);

        if (op != null) {
            ajouterLigne(table, "Date",      op.getDateOperation().format(DATE_FMT), cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Caisse",    op.getCaisse().getLibelle(),            cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Agent",     op.getCaissier().getNomComplet(),       cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Type",      libelleType(op),                        cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Catégorie", op.getCategorie().getLibelle(),         cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Mode régl.",op.getModePaiement().name().replace('_', ' '), cTexte, cTexteSec, tCorps);
            if (op.getReference() != null && !op.getReference().isBlank()) {
                ajouterLigne(table, "Référence", op.getReference(), cTexte, cTexteSec, tCorps);
            }
        } else {
            // Aperçu fictif
            ajouterLigne(table, "Date",      LocalDate.now().format(DATE_COURTE), cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Caisse",    "Caisse — Aperçu",                   cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Agent",     "AGENT TEST",                        cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Type",      "Encaissement",                      cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Catégorie", "Catégorie exemple",                 cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Mode régl.","Espèces",                           cTexte, cTexteSec, tCorps);
        }
        document.add(table);
        saut(document, 4);
    }

    private void ecrireClient(Document document, OperationCaisse op,
                              Color cTexte, Color cTexteSec, int tCorps) throws Exception {
        if (op != null && op.getClient() == null) return;

        Paragraph titre = new Paragraph("CLIENT", font(tCorps - 1, Font.BOLD, cTexteSec));
        document.add(titre);

        PdfPTable table = new PdfPTable(new float[]{35, 65});
        table.setWidthPercentage(100);

        if (op != null) {
            var c = op.getClient();
            if (c.getRaisonSociale() != null && !c.getRaisonSociale().isBlank()) {
                ajouterLigne(table, "M.", c.getRaisonSociale(), cTexte, cTexteSec, tCorps);
            }
            if (c.getTelephone() != null && !c.getTelephone().isBlank()) {
                ajouterLigne(table, "Téléphone", c.getTelephone(), cTexte, cTexteSec, tCorps);
            }
            if (c.getAdresse() != null && !c.getAdresse().isBlank()) {
                ajouterLigne(table, "Adresse", c.getAdresse(), cTexte, cTexteSec, tCorps);
            }
        } else {
            ajouterLigne(table, "M.", "Client exemple",   cTexte, cTexteSec, tCorps);
            ajouterLigne(table, "Adresse", "Dakar",       cTexte, cTexteSec, tCorps);
        }
        document.add(table);
        saut(document, 4);
    }

    private void ecrireMontant(Document document, OperationCaisse op,
                               Color cTexte, Color cFond, Color cSuccess, Color cDanger,
                               int tCorps, int tMontant) throws Exception {
        boolean entree = op == null || "ENTREE".equals(op.getTypeOperation().name());
        BigDecimal montant = op != null ? op.getMontant() : new BigDecimal("10000");

        PdfPTable bloc = new PdfPTable(1);
        bloc.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setBackgroundColor(cFond);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph label = new Paragraph("MONTANT TOTAL", font(tCorps - 1, Font.BOLD, cTexte));
        label.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(label);

        Paragraph m = new Paragraph(
                formatMontant(montant) + " FCFA",
                font(tMontant, Font.BOLD, entree ? cSuccess : cDanger));
        m.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(m);

        bloc.addCell(cell);
        document.add(bloc);
        saut(document, 6);
    }

    private void ecrireMotif(Document document, OperationCaisse op,
                             Color cTexte, Color cTexteSec, int tCorps) throws Exception {
        if (op == null) return;
        String texte = op.getMotif();
        if (texte == null || texte.isBlank()) return;
        Paragraph label = new Paragraph("MOTIF", font(tCorps - 1, Font.BOLD, cTexteSec));
        document.add(label);
        Paragraph m = new Paragraph(texte, font(tCorps, Font.NORMAL, cTexte));
        document.add(m);
        saut(document, 4);
    }

    private void ecrireAnnulation(Document document, OperationCaisse op,
                                  Color cDanger, int tCorps) throws Exception {
        if (op == null || !op.isAnnulee()) return;

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(255, 235, 238));
        cell.setBorderColor(cDanger);
        cell.setBorderWidth(1f);
        cell.setPadding(6);

        Paragraph p1 = new Paragraph("OPÉRATION ANNULÉE", font(tCorps, Font.BOLD, cDanger));
        p1.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p1);

        if (op.getMotifAnnulation() != null) {
            Paragraph p2 = new Paragraph(op.getMotifAnnulation(),
                    font(tCorps - 1, Font.NORMAL, cDanger));
            p2.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(p2);
        }
        table.addCell(cell);
        document.add(table);
        saut(document, 6);
    }

    private void ecrireSignature(Document document, ParametresRecu p,
                                 Color cTexte, Color cTexteSec, int tCorps) throws Exception {
        Paragraph sep = new Paragraph(
                "Signature du Caissier : ___________________________",
                font(tCorps, Font.NORMAL, cTexte));
        document.add(sep);

        String ville = blankIfNull(p.getVilleSignature(), "Dakar");
        Paragraph date = new Paragraph(
                ville + ", le " + LocalDate.now().format(DATE_COURTE),
                font(tCorps, Font.NORMAL, cTexte));
        date.setSpacingBefore(6);
        document.add(date);
        saut(document, 4);
    }

    private void ecrirePiedPage(Document document, ParametresRecu p,
                                Color cAccent, Color cTexte, Color cTexteSec, int tFooter) throws Exception {
        String ligne1 = blankIfNull(p.getFooterLigne1(), "Merci de votre passage.");
        String ligne2 = blankIfNull(p.getFooterLigne2(), "RTS - Conservez ce reçu.");

        Paragraph l1 = new Paragraph(ligne1, font(tFooter + 1, Font.ITALIC, cTexte));
        l1.setAlignment(Element.ALIGN_CENTER);
        document.add(l1);

        Paragraph l2 = new Paragraph(ligne2, font(tFooter, Font.NORMAL, cTexteSec));
        l2.setAlignment(Element.ALIGN_CENTER);
        l2.setSpacingBefore(2);
        document.add(l2);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private List<ParametresRecuDto.Section> sectionsOrdonnees(ParametresRecu params) {
        String json = params.getLayoutJson();
        if (json == null || json.isBlank()) {
            return sectionsDefaut();
        }
        try {
            List<ParametresRecuDto.Section> sections = objectMapper.readValue(
                    json, new TypeReference<List<ParametresRecuDto.Section>>() {});
            return sections != null && !sections.isEmpty() ? sections : sectionsDefaut();
        } catch (Exception e) {
            log.warn("layout_json corrompu, fallback ordre par défaut : {}", e.getMessage());
            return sectionsDefaut();
        }
    }

    private List<ParametresRecuDto.Section> sectionsDefaut() {
        return List.of(
                new ParametresRecuDto.Section("header",     true),
                new ParametresRecuDto.Section("titre",      true),
                new ParametresRecuDto.Section("numero",     true),
                new ParametresRecuDto.Section("details",    true),
                new ParametresRecuDto.Section("client",     true),
                new ParametresRecuDto.Section("montant",    true),
                new ParametresRecuDto.Section("motif",      true),
                new ParametresRecuDto.Section("annulation", true),
                new ParametresRecuDto.Section("signature",  true),
                new ParametresRecuDto.Section("footer",     true)
        );
    }

    private void addParag(PdfPCell cell, String texte, Font f) {
        if (texte == null || texte.isBlank()) return;
        Paragraph p = new Paragraph(texte, f);
        cell.addElement(p);
    }

    private String libelleType(OperationCaisse op) {
        if (op == null) return "—";
        return "ENTREE".equals(op.getTypeOperation().name()) ? "Encaissement" : "Décaissement";
    }

    private void ajouterLigne(PdfPTable table, String label, String valeur,
                              Color cTexte, Color cTexteSec, int tCorps) {
        PdfPCell cLabel = new PdfPCell(new Paragraph(label + " :",
                font(tCorps - 1, Font.BOLD, cTexteSec)));
        cLabel.setBorder(PdfPCell.NO_BORDER);
        cLabel.setPaddingTop(3);
        cLabel.setPaddingBottom(3);
        table.addCell(cLabel);

        PdfPCell cValeur = new PdfPCell(new Paragraph(valeur == null ? "—" : valeur,
                font(tCorps, Font.NORMAL, cTexte)));
        cValeur.setBorder(PdfPCell.NO_BORDER);
        cValeur.setPaddingTop(3);
        cValeur.setPaddingBottom(3);
        table.addCell(cValeur);
    }

    private Font font(float size, int style, Color color) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        f.setColor(color);
        return f;
    }

    private void saut(Document document, float height) throws Exception {
        Paragraph p = new Paragraph(new Chunk(" "));
        p.setSpacingAfter(height);
        document.add(p);
    }

    private String formatMontant(BigDecimal montant) {
        return montant == null ? "0" : MONTANT_FMT.format(montant);
    }

    private static Color hex(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int nz(Integer v, int fallback) {
        return v != null && v > 0 ? v : fallback;
    }

    private static String blankIfNull(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}

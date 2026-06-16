package sn.rts.caisse.service;

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
import sn.rts.caisse.dto.VentilationRecetteResponse;
import sn.rts.caisse.dto.VentilationRecetteResponse.FicheReference;
import sn.rts.caisse.dto.VentilationRecetteResponse.LigneVentilation;
import sn.rts.caisse.dto.VentilationRecetteResponse.ReversementLigne;
import sn.rts.caisse.model.ParametresRecu;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Génère le PDF de la « Ventilation hebdomadaire des recettes », fidèle à la
 * fiche papier RTS : en-tête, tableau Produits (HT / Timbre / TTC) + total,
 * blocs « Fiche de références » et « Reversements des produits », et la double
 * signature (Contrôle 1 — Chef Unité Finances / Contrôle 2 — Chef de Département).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecetteHebdoPdfService {

    private final ParametresRecuService parametresService;

    private static final DateTimeFormatter D  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final NumberFormat MT;
    static {
        MT = NumberFormat.getNumberInstance(Locale.FRANCE);
        MT.setMinimumFractionDigits(0);
        MT.setMaximumFractionDigits(0);
    }

    private static final Color RTS  = new Color(227, 6, 19);
    private static final Color DARK = new Color(26, 26, 26);
    private static final Color GREY = new Color(110, 110, 110);
    private static final Color HEAD = new Color(243, 230, 231);
    private static final Color LINE = new Color(200, 200, 200);

    public byte[] genererPdf(VentilationRecetteResponse v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            enTete(doc, v);
            titre(doc, v);
            tableauProduits(doc, v);
            saut(doc, 10);
            blocsReferencesEtReversements(doc, v);
            saut(doc, 16);
            signatures(doc, v);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Échec génération PDF ventilation recettes", e);
            throw new RuntimeException("Impossible de générer le PDF de ventilation : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------

    private void enTete(Document doc, VentilationRecetteResponse v) {
        // ----- Logo RTS aligné à GAUCHE -----
        Image logo = chargerLogo();
        if (logo != null) {
            logo.scaleToFit(80, 80);
            logo.setAlignment(Image.ALIGN_LEFT);
            doc.add(logo);
        } else {
            // Repli ultime : pastille rouge "RTS"
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(20);
            box.setHorizontalAlignment(Element.ALIGN_LEFT);
            PdfPCell c = new PdfPCell(new Paragraph("RTS", font(22, Font.BOLD, Color.WHITE)));
            c.setBackgroundColor(RTS);
            c.setBorder(PdfPCell.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(10);
            box.addCell(c);
            doc.add(box);
        }

        // ----- Raison sociale + caisse, SOUS le logo -----
        Paragraph nom = new Paragraph("Radiodiffusion Télévision Sénégalaise", font(11, Font.BOLD, RTS));
        nom.setSpacingBefore(8);
        doc.add(nom);
        Paragraph caisse = new Paragraph(v.caisseCode() + " — " + v.caisseLibelle(), font(9, Font.NORMAL, GREY));
        doc.add(caisse);
        saut(doc, 10);
    }

    /**
     * Logo RTS : 1) image personnalisée uploadée (ParametresRecu) si disponible,
     * sinon 2) logo RTS embarqué dans le classpath, sinon null (pastille texte).
     */
    private Image chargerLogo() {
        try {
            ParametresRecu params = parametresService.obtenirEntite();
            if (params != null && params.getLogoImage() != null && params.getLogoImage().length > 0) {
                return Image.getInstance(params.getLogoImage());
            }
        } catch (Exception ex) {
            log.debug("Pas de logo paramétré ({}), repli sur le logo embarqué.", ex.getMessage());
        }
        try (java.io.InputStream in = getClass().getResourceAsStream("/images/rts-logo.jpg")) {
            if (in != null) {
                return Image.getInstance(in.readAllBytes());
            }
        } catch (Exception ex) {
            log.warn("Logo RTS embarqué illisible : {}", ex.getMessage());
        }
        return null;
    }

    private void titre(Document doc, VentilationRecetteResponse v) {
        Paragraph titre = new Paragraph("VENTILATION HEBDOMADAIRE DES RECETTES", font(13, Font.BOLD, DARK));
        titre.setAlignment(Element.ALIGN_CENTER);
        doc.add(titre);

        Paragraph semaine = new Paragraph(
                "Semaine du " + v.dateDebut().format(D) + " au " + v.dateFin().format(D)
                        + "        Statut : " + libelleStatut(v.statut()),
                font(9, Font.NORMAL, GREY));
        semaine.setAlignment(Element.ALIGN_CENTER);
        semaine.setSpacingBefore(2);
        doc.add(semaine);
        saut(doc, 10);
    }

    private void tableauProduits(Document doc, VentilationRecetteResponse v) {
        PdfPTable t = new PdfPTable(new float[]{46, 18, 18, 18});
        t.setWidthPercentage(100);

        entete(t, "PRODUITS", Element.ALIGN_LEFT);
        entete(t, "MONTANT HT", Element.ALIGN_RIGHT);
        entete(t, "TIMBRE", Element.ALIGN_RIGHT);
        entete(t, "MONTANT TTC", Element.ALIGN_RIGHT);

        if (v.lignes().isEmpty()) {
            PdfPCell vide = new PdfPCell(new Paragraph("Aucune recette sur la période.", font(9, Font.ITALIC, GREY)));
            vide.setColspan(4);
            vide.setPadding(8);
            vide.setHorizontalAlignment(Element.ALIGN_CENTER);
            vide.setBorderColor(LINE);
            t.addCell(vide);
        } else {
            for (LigneVentilation l : v.lignes()) {
                corps(t, l.produitLibelle(), Element.ALIGN_LEFT, false);
                corps(t, montant(l.montantHt()), Element.ALIGN_RIGHT, false);
                corps(t, montant(l.timbre()), Element.ALIGN_RIGHT, false);
                corps(t, montant(l.montantTtc()), Element.ALIGN_RIGHT, false);
            }
        }

        // Ligne TOTAL RECETTES
        PdfPCell tot = new PdfPCell(new Paragraph("TOTAL RECETTES", font(9.5f, Font.BOLD, DARK)));
        tot.setBackgroundColor(HEAD);
        tot.setBorderColor(LINE);
        tot.setPadding(6);
        t.addCell(tot);
        totalCell(t, v.totalHt());
        totalCell(t, v.totalTimbre());
        totalCell(t, v.totalTtc());

        doc.add(t);
    }

    private void blocsReferencesEtReversements(Document doc, VentilationRecetteResponse v) throws Exception {
        PdfPTable outer = new PdfPTable(new float[]{50, 50});
        outer.setWidthPercentage(100);

        // -------- Fiche de références --------
        PdfPCell gauche = new PdfPCell();
        gauche.setBorder(PdfPCell.NO_BORDER);
        gauche.setPaddingRight(8);
        gauche.addElement(sousTitre("FICHE DE RÉFÉRENCES"));
        PdfPTable fr = new PdfPTable(new float[]{40, 30, 30});
        fr.setWidthPercentage(100);
        entete(fr, "N° FICHES", Element.ALIGN_LEFT);
        entete(fr, "DATE", Element.ALIGN_CENTER);
        entete(fr, "MONTANT", Element.ALIGN_RIGHT);
        if (v.fiches().isEmpty()) {
            ligneVide(fr, 3);
        } else {
            for (FicheReference f : v.fiches()) {
                corps(fr, f.numeroRecu(), Element.ALIGN_LEFT, true);
                corps(fr, dateCourte(f.date()), Element.ALIGN_CENTER, true);
                corps(fr, montant(f.montant()), Element.ALIGN_RIGHT, true);
            }
        }
        gauche.addElement(fr);
        outer.addCell(gauche);

        // -------- Reversements des produits --------
        PdfPCell droite = new PdfPCell();
        droite.setBorder(PdfPCell.NO_BORDER);
        droite.setPaddingLeft(8);
        droite.addElement(sousTitre("REVERSEMENTS DES PRODUITS"));
        PdfPTable rv = new PdfPTable(new float[]{28, 42, 30});
        rv.setWidthPercentage(100);
        entete(rv, "DATE", Element.ALIGN_LEFT);
        entete(rv, "RÉF. B. VERSEMENTS", Element.ALIGN_CENTER);
        entete(rv, "MONTANT", Element.ALIGN_RIGHT);
        if (v.reversements().isEmpty()) {
            ligneVide(rv, 3);
        } else {
            for (ReversementLigne r : v.reversements()) {
                corps(rv, dateCourte(r.date()), Element.ALIGN_LEFT, true);
                corps(rv, r.refBordereau(), Element.ALIGN_CENTER, true);
                corps(rv, montant(r.montant()), Element.ALIGN_RIGHT, true);
            }
        }
        // Ligne TOTAL VENTE
        PdfPCell labelTot = new PdfPCell(new Paragraph("TOTAL VENTE", font(8.5f, Font.BOLD, DARK)));
        labelTot.setColspan(2);
        labelTot.setBackgroundColor(HEAD);
        labelTot.setBorderColor(LINE);
        labelTot.setPadding(5);
        rv.addCell(labelTot);
        totalCell(rv, v.totalReversements());
        droite.addElement(rv);
        outer.addCell(droite);

        doc.add(outer);
    }

    private void signatures(Document doc, VentilationRecetteResponse v) {
        PdfPTable t = new PdfPTable(new float[]{50, 50});
        t.setWidthPercentage(100);

        t.addCell(caseControle("CONTRÔLE 1", "Le Chef Unité Finances",
                v.controle1ParNom(), v.controle1Le()));
        t.addCell(caseControle("CONTRÔLE 2", "Le Chef de Département",
                v.controle2ParNom(), v.controle2Le()));

        doc.add(t);
    }

    private PdfPCell caseControle(String titre, String fonction, String nom, LocalDateTime le) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINE);
        c.setPadding(10);
        c.addElement(new Paragraph(titre, font(9.5f, Font.BOLD, DARK)));

        Paragraph etat = new Paragraph(
                nom != null
                        ? "Validé par : " + nom + (le != null ? "  (" + le.format(DT) + ")" : "")
                        : "En attente de signature",
                font(8.5f, nom != null ? Font.NORMAL : Font.ITALIC, nom != null ? DARK : GREY));
        etat.setSpacingBefore(10);
        c.addElement(etat);

        Paragraph sign = new Paragraph(fonction, font(8.5f, Font.BOLD, GREY));
        sign.setSpacingBefore(22);
        c.addElement(sign);
        return c;
    }

    // ------------------------------------------------------------------
    //  Helpers de rendu
    // ------------------------------------------------------------------

    private void entete(PdfPTable t, String texte, int align) {
        PdfPCell c = new PdfPCell(new Paragraph(texte, font(8.5f, Font.BOLD, DARK)));
        c.setBackgroundColor(HEAD);
        c.setBorderColor(LINE);
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        t.addCell(c);
    }

    private void corps(PdfPTable t, String texte, int align, boolean petit) {
        PdfPCell c = new PdfPCell(new Paragraph(texte == null ? "" : texte,
                font(petit ? 8f : 9f, Font.NORMAL, DARK)));
        c.setBorderColor(LINE);
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        t.addCell(c);
    }

    private void totalCell(PdfPTable t, BigDecimal val) {
        PdfPCell c = new PdfPCell(new Paragraph(montant(val), font(9.5f, Font.BOLD, RTS)));
        c.setBackgroundColor(HEAD);
        c.setBorderColor(LINE);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(6);
        t.addCell(c);
    }

    private void ligneVide(PdfPTable t, int colspan) {
        PdfPCell c = new PdfPCell(new Paragraph("—", font(8.5f, Font.ITALIC, GREY)));
        c.setColspan(colspan);
        c.setBorderColor(LINE);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(6);
        t.addCell(c);
    }

    private Paragraph sousTitre(String texte) {
        Paragraph p = new Paragraph(texte, font(9, Font.BOLD, DARK));
        p.setSpacingAfter(4);
        return p;
    }

    private Font font(float size, int style, Color color) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        f.setColor(color);
        return f;
    }

    private void saut(Document doc, float h) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(h);
        doc.add(p);
    }

    private String montant(BigDecimal v) {
        return (v == null ? "0" : MT.format(v));
    }

    private String dateCourte(LocalDateTime d) {
        return d == null ? "" : d.format(D);
    }

    private String libelleStatut(String s) {
        return switch (s) {
            case "BROUILLON" -> "Brouillon";
            case "CONTROLE_1" -> "Contrôle 1 effectué";
            case "VALIDEE" -> "Validée";
            default -> s;
        };
    }
}

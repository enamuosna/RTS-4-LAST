/* Génère les deux livrables Word du projet RTS Caisse :
   1. Manuel_Utilisation_RTS_Caisse.docx
   2. Architecture_et_Choix_Techniques_RTS_Caisse.docx
*/
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, LevelFormat, HeadingLevel, BorderStyle,
  WidthType, ShadingType, VerticalAlign, PageNumber, PageBreak,
  TableOfContents, TabStopType, TabStopPosition
} = require("docx");

const OUT_DIR = __dirname;
const CONTENT_WIDTH = 9360;       // US Letter, marges 1"
const RTS_RED = "B30510";
const RTS_RED_LIGHT = "E30613";
const HEAD_FILL = "F3E6E7";       // rose très clair pour en-têtes de tableaux
const GREY = "666666";
const BORDER = { style: BorderStyle.SINGLE, size: 4, color: "CCCCCC" };
const BORDERS = { top: BORDER, bottom: BORDER, left: BORDER, right: BORDER };

// ---------- numérotation (puces + listes ordonnées multiples) ----------
const numberingConfig = [
  { reference: "bullets", levels: [
    { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 560, hanging: 280 } } } },
    { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 1040, hanging: 280 } } } },
  ] },
];
for (let i = 0; i < 60; i++) {
  numberingConfig.push({
    reference: "ord" + i,
    levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 560, hanging: 320 } } } }],
  });
}
let ordCounter = 0;
function nextOrd() { return "ord" + (ordCounter++); }

// ---------- helpers de contenu ----------
function H1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    children: [new TextRun(text)],
    border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: RTS_RED_LIGHT, space: 4 } },
  });
}
function H2(text) { return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] }); }
function H3(text) { return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] }); }

function P(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [new TextRun(text)];
  return new Paragraph({ children: runs, spacing: { after: 120, line: 276 }, ...opts });
}
function bold(t) { return new TextRun({ text: t, bold: true }); }
function run(t) { return new TextRun(t); }

function bullet(text, level = 0) {
  const runs = Array.isArray(text) ? text : [new TextRun(text)];
  return new Paragraph({ numbering: { reference: "bullets", level }, spacing: { after: 60, line: 270 }, children: runs });
}
function step(ref, text) {
  const runs = Array.isArray(text) ? text : [new TextRun(text)];
  return new Paragraph({ numbering: { reference: ref, level: 0 }, spacing: { after: 60, line: 270 }, children: runs });
}

function cell(content, { width, fill, boldText = false, header = false, align } = {}) {
  const paras = (Array.isArray(content) ? content : [content]).map((c) =>
    new Paragraph({
      alignment: align,
      spacing: { after: 0, line: 264 },
      children: [new TextRun({ text: c, bold: boldText || header, color: header ? RTS_RED : undefined })],
    }));
  return new TableCell({
    borders: BORDERS,
    width: { size: width, type: WidthType.DXA },
    shading: header ? { fill: HEAD_FILL, type: ShadingType.CLEAR } : undefined,
    margins: { top: 70, bottom: 70, left: 120, right: 120 },
    verticalAlign: VerticalAlign.CENTER,
    children: paras,
  });
}
function makeTable(headers, rows, colWidths) {
  const headerRow = new TableRow({
    tableHeader: true,
    children: headers.map((h, i) => cell(h, { width: colWidths[i], header: true })),
  });
  const bodyRows = rows.map((r) =>
    new TableRow({ children: r.map((c, i) => cell(c, { width: colWidths[i] })) }));
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [headerRow, ...bodyRows],
  });
}
function spacer(h = 120) { return new Paragraph({ spacing: { after: h }, children: [new TextRun("")] }); }

// ---------- page de garde ----------
function coverPage(title, subtitle, meta) {
  const children = [];
  children.push(new Paragraph({ spacing: { before: 1200, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "RTS", bold: true, size: 96, color: RTS_RED_LIGHT })] }));
  children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 },
    children: [new TextRun({ text: "Radiodiffusion Télévision Sénégalaise", size: 22, color: GREY })] }));
  children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 600, after: 80 },
    children: [new TextRun({ text: "RTS Caisse", bold: true, size: 40, color: "1A1A1A" })] }));
  children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 40 },
    children: [new TextRun({ text: title, bold: true, size: 56, color: RTS_RED })] }));
  children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 1000 },
    children: [new TextRun({ text: subtitle, italics: true, size: 26, color: GREY })] }));

  const metaRows = meta.map(([k, v]) =>
    new TableRow({ children: [
      cell(k, { width: 3000, boldText: true }),
      cell(v, { width: 4200 }),
    ] }));
  children.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun("")] }));
  const metaTable = new Table({
    width: { size: 7200, type: WidthType.DXA },
    columnWidths: [3000, 4200],
    alignment: AlignmentType.CENTER,
    rows: metaRows,
  });
  children.push(metaTable);
  children.push(new Paragraph({ children: [new PageBreak()] }));
  return children;
}

function tocPage() {
  return [
    new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun("Sommaire")] }),
    new TableOfContents("Sommaire", { hyperlink: true, headingStyleRange: "1-3" }),
    new Paragraph({ children: [new PageBreak()] }),
  ];
}

// ---------- styles & document factory ----------
function buildDoc(docTitle, sectionsChildren) {
  return new Document({
    creator: "RTS — Projet RTS Caisse",
    title: docTitle,
    styles: {
      default: { document: { run: { font: "Arial", size: 22, color: "1A1A1A" } } },
      paragraphStyles: [
        { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 30, bold: true, font: "Arial", color: RTS_RED },
          paragraph: { spacing: { before: 320, after: 160 }, outlineLevel: 0, keepNext: true } },
        { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 25, bold: true, font: "Arial", color: "1A1A1A" },
          paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1, keepNext: true } },
        { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 22, bold: true, font: "Arial", color: RTS_RED },
          paragraph: { spacing: { before: 180, after: 80 }, outlineLevel: 2, keepNext: true } },
      ],
    },
    numbering: { config: numberingConfig },
    sections: [{
      properties: { page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      } },
      headers: { default: new Header({ children: [ new Paragraph({
        tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH }],
        border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "DDDDDD", space: 2 } },
        children: [
          new TextRun({ text: "RTS Caisse", bold: true, size: 16, color: RTS_RED }),
          new TextRun({ text: "\t" + docTitle, size: 16, color: GREY }),
        ] }) ] }) },
      footers: { default: new Footer({ children: [ new Paragraph({
        alignment: AlignmentType.CENTER,
        border: { top: { style: BorderStyle.SINGLE, size: 4, color: "DDDDDD", space: 2 } },
        children: [
          new TextRun({ text: "© 2026 RTS Sénégal — Confidentiel    |    Page ", size: 16, color: GREY }),
          new TextRun({ children: [PageNumber.CURRENT], size: 16, color: GREY }),
          new TextRun({ text: " / ", size: 16, color: GREY }),
          new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 16, color: GREY }),
        ] }) ] }) },
      children: sectionsChildren,
    }],
  });
}

// ============================================================
//  DOCUMENT 1 — MANUEL D'UTILISATION
// ============================================================
function manuel() {
  const c = [];
  const META = [
    ["Projet", "RTS Caisse — Gestion des encaissements de guichet"],
    ["Document", "Manuel d'utilisation"],
    ["Public visé", "Caissiers, agents de recette, superviseurs, administrateurs"],
    ["Version", "1.3"],
    ["Date", "12 juin 2026"],
    ["Établi par", "Ansou CAMARA"],
  ];
  c.push(...coverPage("Manuel d'utilisation", "Guide pas à pas du client de guichet et de l'application d'administration", META));
  c.push(...tocPage());

  // 1. Présentation
  c.push(H1("1. Présentation du système"));
  c.push(P([bold("RTS Caisse"), run(" est le système de gestion des encaissements et décaissements des guichets de la Radiodiffusion Télévision Sénégalaise (RTS). Il couvre l'ensemble du cycle d'une opération de caisse : ouverture de caisse, saisie des encaissements, édition et envoi du reçu au client, versements bancaires, puis clôture et contrôle.")]));
  c.push(P("Le système est composé de trois applications complémentaires :"));
  c.push(bullet([bold("Le client Guichet"), run(" : application de bureau (Windows) installée au poste du caissier pour la saisie quotidienne des opérations.")]));
  c.push(bullet([bold("L'application Web d'administration"), run(" : accessible au navigateur pour le pilotage, la supervision et le paramétrage.")]));
  c.push(bullet([bold("Le serveur central (backend)"), run(" : héberge les données, applique les règles de gestion et la sécurité. Les deux applications s'y connectent.")]));

  c.push(H2("1.1. Les rôles utilisateurs"));
  c.push(P("Chaque utilisateur reçoit un rôle qui détermine ce qu'il peut voir et faire :"));
  c.push(makeTable(
    ["Rôle", "Application", "Droits principaux"],
    [
      ["ADMIN", "Web", "Administration globale : utilisateurs, caisses, banques, produits, paramétrage du reçu et du timbre, sauvegarde."],
      ["SUPERVISEUR", "Web", "Tableaux de bord, supervision des caisses, validation des clôtures et des écarts."],
      ["CAISSIER", "Guichet", "Ouvre sa caisse, saisit les opérations et versements, imprime/envoie les reçus, clôture sa caisse."],
      ["AGENT_RECETTE", "Guichet", "Superviseur de proximité d'une caisse : peut en plus modifier et réactiver les opérations de SA caisse."],
      ["CONTROLEUR", "Web", "Accès en lecture seule : consulte opérations, versements, journaux et supervision, sans rien modifier."],
    ],
    [2000, 1700, 5660]
  ));
  c.push(spacer());

  // 2. Démarrage du guichet
  c.push(H1("2. Démarrer le client Guichet"));
  c.push(H2("2.1. Installation"));
  c.push(P("Le client est distribué sous forme d'un installateur Windows (.msi). Pour l'installer :"));
  let r = nextOrd();
  c.push(step(r, "Double-cliquez sur le fichier « RTS Caisse Client-x.y.z.msi » (ou lancez-le via msiexec)."));
  c.push(step(r, "Acceptez l'élévation administrateur demandée par Windows."));
  c.push(step(r, "Suivez l'assistant ; un raccourci « RTS Caisse Client » est créé dans le menu Démarrer (groupe RTS)."));
  c.push(P([run("Lors d'une mise à jour, installez simplement la nouvelle version : elle remplace automatiquement l'ancienne. "), bold("Fermez l'application avant la mise à jour.")]));

  c.push(H2("2.2. Connexion au serveur"));
  c.push(P("Au premier lancement, l'application affiche l'écran « Connexion au serveur ». Saisissez l'adresse du serveur central RTS Caisse (fournie par votre administrateur, par exemple http://srv-caisse.rts.local:9090/api), puis validez. Ce réglage est mémorisé."));

  c.push(H2("2.3. Authentification"));
  c.push(P("Saisissez votre identifiant et votre mot de passe. Seuls les comptes de type CAISSIER et AGENT_RECETTE peuvent accéder au guichet ; les autres rôles sont orientés vers l'application Web."));

  // 3. Utilisation du guichet
  c.push(H1("3. Utiliser le Guichet (caissier)"));

  c.push(H2("3.1. Sélectionner sa caisse"));
  c.push(P("Après connexion, la liste des caisses qui vous sont affectées s'affiche. Sélectionnez la caisse sur laquelle vous travaillez pour ouvrir le tableau de bord. Une caisse suspendue ne peut pas être utilisée."));

  c.push(H2("3.2. Ouvrir la caisse"));
  c.push(P([run("Une caisse est "), bold("FERMÉE"), run(" par défaut. Cliquez sur "), bold("Ouvrir la caisse"), run(" et saisissez le "), bold("fond de caisse"), run(" (montant en espèces présent dans le tiroir au démarrage). La caisse passe alors à l'état "), bold("OUVERTE"), run(" et vous pouvez saisir des opérations.")]));

  c.push(H2("3.3. Le tableau de bord"));
  c.push(P("Le tableau de bord présente en permanence :"));
  c.push(bullet([bold("Le statut de la caisse"), run(" (OUVERTE / FERMÉE) et le solde courant.")]));
  c.push(bullet([bold("Les opérations du jour"), run(" sous forme de tableau (heure, n° de reçu, type, montant, actions).")]));
  c.push(bullet([bold("Une barre de recherche"), run(" pour filtrer les opérations (produit, client, montant…).")]));
  c.push(bullet([bold("Les boutons d'action"), run(" : Nouvelle opération, Versement bancaire, Clôturer la caisse, basculer le thème clair/sombre, Déconnexion.")]));

  c.push(H2("3.4. Saisir une nouvelle opération"));
  c.push(P([run("Cliquez sur "), bold("Nouvelle opération"), run(". Une fenêtre de saisie s'ouvre ; vous pouvez désormais la "), bold("réduire, l'agrandir et la redimensionner"), run(". Renseignez les champs :")]));
  c.push(makeTable(
    ["Champ", "Obligatoire", "Description"],
    [
      ["Produit", "Oui", "Nature de l'encaissement (ex. « Vente d'archives »), choisi dans la liste des produits paramétrés."],
      ["Mode de paiement", "Oui", "Espèces, Chèque ou Virement. Pour Chèque/Virement, une section Banque apparaît."],
      ["Montant HT (FCFA)", "Oui", "Montant hors timbre saisi par le caissier."],
      ["Référence", "Non", "Référence libre (n° de chèque, n° de pièce…)."],
      ["Timbre (FCFA)", "Selon produit", "Timbre fiscal, calculé/pré-rempli selon le paramétrage."],
      ["Montant TTC", "Calculé", "Somme Montant HT + Timbre, affichée automatiquement."],
      ["Date / Heure de diffusion", "Selon produit", "Pour les produits liés à une diffusion (publicité, communiqué…)."],
      ["Client", "Non", "Client existant (recherche) ou nouveau client (+ Nouveau)."],
      ["Justificatif", "Non", "Pièce jointe (image/PDF, 10 Mo max) selon le type d'opération."],
    ],
    [2300, 1500, 5560]
  ));
  c.push(P([run("Cliquez sur "), bold("Enregistrer l'opération"), run(". Un numéro de reçu est attribué automatiquement et l'opération apparaît dans la liste du jour.")]));

  c.push(H3("Imprimer ou envoyer le reçu"));
  c.push(P([run("Juste après l'enregistrement, une fenêtre propose : "), bold("Imprimer"), run(" le reçu, l'envoyer par "), bold("WhatsApp"), run(" au client, ou "), bold("Fermer"), run(". L'envoi WhatsApp nécessite que le service soit activé côté serveur et qu'un numéro de téléphone soit renseigné.")]));

  c.push(H2("3.5. Enregistrer un versement bancaire"));
  c.push(P([run("Cliquez sur "), bold("Versement bancaire"), run(" pour déclarer un dépôt d'espèces à la banque. Renseignez la banque destinataire, le montant, le numéro de bordereau et joignez le bordereau scanné si nécessaire, puis enregistrez.")]));

  c.push(H2("3.6. Modifier, annuler ou réactiver une opération"));
  c.push(P([run("Depuis la liste des opérations, des actions sont disponibles selon votre rôle. Le "), bold("CAISSIER"), run(" peut annuler une opération erronée (avec motif). L'"), bold("AGENT_RECETTE"), run(" peut en plus "), bold("modifier"), run(" et "), bold("réactiver"), run(" une opération de sa caisse. La caisse doit être ouverte pour ces actions.")]));

  c.push(H2("3.7. Clôturer la caisse"));
  c.push(P([run("En fin de journée, cliquez sur "), bold("Clôturer la caisse"), run(". Saisissez le "), bold("solde réel"), run(" compté dans le tiroir. Le système calcule le résumé : fond d'ouverture, total des entrées et sorties, solde théorique, solde réel et "), bold("écart"), run(".")]));
  c.push(bullet("Écart nul : clôture parfaite."));
  c.push(bullet("Écart positif : excédent de caisse."));
  c.push(bullet("Écart négatif : manquant en caisse."));
  c.push(P("La caisse repasse à l'état FERMÉE. La clôture reste à valider par un superviseur depuis l'application Web."));

  c.push(H2("3.8. Confort d'utilisation"));
  c.push(bullet([bold("Thème clair / sombre"), run(" : basculez d'un clic ; le choix est mémorisé.")]));
  c.push(bullet([bold("Fenêtres redimensionnables"), run(" : les fenêtres de saisie peuvent être réduites, agrandies et redimensionnées.")]));
  c.push(bullet([bold("Boîtes de dialogue"), run(" : messages d'information, de confirmation et d'erreur aux couleurs RTS (vert succès, ambre avertissement, rouge erreur).")]));
  c.push(bullet([bold("Déconnexion"), run(" : si la caisse est encore ouverte, une confirmation est demandée.")]));

  // 4. Application web
  c.push(new Paragraph({ children: [new PageBreak()] }));
  c.push(H1("4. Utiliser l'application Web (administration & supervision)"));
  c.push(P("L'application Web s'ouvre dans un navigateur. Après authentification, le menu donne accès aux modules suivants (selon votre rôle) :"));
  c.push(makeTable(
    ["Module", "À quoi il sert"],
    [
      ["Tableau de bord", "Indicateurs clés : encaissements, état des caisses, tendances."],
      ["Supervision", "Vue temps réel de l'état des caisses et détail par caisse ; validation des clôtures et des écarts (superviseur)."],
      ["Utilisateurs", "Création/gestion des comptes et attribution des rôles (admin)."],
      ["Caisses", "Création des caisses, affectation des caissiers, types d'opération autorisés, suspension."],
      ["Banques", "Référentiel des banques destinataires des versements."],
      ["Produits / Catégories", "Référentiel des produits encaissables (catégories d'opération)."],
      ["Clients", "Référentiel des clients (raison sociale, contact, téléphone WhatsApp)."],
      ["Journaux", "Journaux de caisse (ouvertures/clôtures) ; export Excel."],
      ["Opérations", "Consultation et recherche des opérations ; purge définitive (admin)."],
      ["Versements", "Suivi des versements bancaires."],
      ["Paramètres du reçu", "Personnalisation de l'en-tête, du logo et des mentions du reçu PDF."],
      ["Timbre", "Configuration du timbre fiscal (montants, application au TTC)."],
      ["Audit", "Journal d'audit : qui a fait quoi et quand (traçabilité)."],
      ["Sauvegarde / Maintenance", "Sauvegarde de la base et opérations de maintenance (admin)."],
    ],
    [2600, 6760]
  ));

  c.push(H2("4.1. Valider une clôture (superviseur)"));
  c.push(P("Dans Supervision, ouvrez la caisse clôturée, vérifiez le résumé et l'écart éventuel, puis validez la clôture. Cette validation finalise la journée de caisse."));

  c.push(H2("4.2. Tracer les actions (audit)"));
  c.push(P("Le module Audit conserve l'historique des actions sensibles (connexions, créations, modifications, annulations, purges…). Il permet de filtrer par utilisateur, action et période pour le contrôle interne."));

  // 5. FAQ
  c.push(H1("5. Bonnes pratiques & dépannage"));
  c.push(makeTable(
    ["Situation", "Que faire"],
    [
      ["« Vous devez d'abord ouvrir la caisse »", "Ouvrez la caisse (fond de caisse) avant toute saisie d'opération."],
      ["Le reçu ne s'imprime pas", "Vérifiez l'imprimante par défaut ; réessayez depuis la liste des opérations."],
      ["L'envoi WhatsApp échoue", "Vérifiez le numéro du client et que le service WhatsApp est activé côté serveur."],
      ["Impossible de se connecter", "Vérifiez l'adresse du serveur (écran Connexion au serveur) et le réseau."],
      ["Écart à la clôture", "Recomptez le tiroir ; l'écart est enregistré et tranché par le superviseur."],
      ["Erreur de saisie sur une opération", "Le caissier annule avec motif ; l'agent de recette peut modifier/réactiver."],
    ],
    [3400, 5960]
  ));
  c.push(spacer());
  c.push(P([new TextRun({ text: "Pour toute question fonctionnelle, contactez votre superviseur ; pour un incident technique, l'administrateur du système RTS Caisse.", italics: true, color: GREY })]));

  return buildDoc("Manuel d'utilisation", c);
}

// ============================================================
//  DOCUMENT 2 — ARCHITECTURE & CHOIX TECHNIQUES
// ============================================================
function architecture() {
  const c = [];
  const META = [
    ["Projet", "RTS Caisse — Gestion des encaissements de guichet"],
    ["Document", "Architecture technique & justification des choix"],
    ["Public visé", "Équipe technique, encadrants, mainteneurs"],
    ["Version", "1.3"],
    ["Date", "12 juin 2026"],
    ["Établi par", "Ansou CAMARA"],
  ];
  c.push(...coverPage("Architecture & choix techniques", "Pile technologique, découpage applicatif et justification des décisions", META));
  c.push(...tocPage());

  // 1. Vue d'ensemble
  c.push(H1("1. Vue d'ensemble de l'architecture"));
  c.push(P([run("RTS Caisse adopte une architecture "), bold("3-tiers à clients multiples"), run(" : un serveur central unique expose une API REST, consommée par deux applications clientes distinctes — un "), bold("client lourd de bureau"), run(" pour les guichets et une "), bold("application web"), run(" pour l'administration. Les données sont centralisées dans une base relationnelle.")]));
  c.push(P("Le schéma logique est le suivant :"));
  c.push(makeTable(
    ["Couche", "Composant", "Technologie", "Rôle"],
    [
      ["Présentation (guichet)", "Client Guichet", "JavaFX 21 (desktop Windows)", "Saisie quotidienne des caissiers, impression des reçus."],
      ["Présentation (admin)", "Application Web", "Angular 17 + Angular Material", "Pilotage, supervision, paramétrage."],
      ["Application / Métier", "Backend API REST", "Spring Boot 3.3 (Java 21)", "Règles de gestion, sécurité, exports, audit."],
      ["Données", "Base de données", "PostgreSQL + Flyway", "Stockage transactionnel et historique."],
    ],
    [2100, 1900, 2360, 3000]
  ));
  c.push(spacer());
  c.push(P([run("Les échanges client → serveur se font en "), bold("HTTP/JSON"), run(", sécurisés par "), bold("jeton JWT"), run(". Le serveur est la seule autorité sur les données : les clients ne contiennent aucune logique de persistance, ce qui garantit la cohérence entre le guichet et le web.")]));

  // 2. Pile technologique
  c.push(H1("2. Pile technologique (synthèse)"));
  c.push(makeTable(
    ["Domaine", "Technologie", "Version"],
    [
      ["Langage backend & guichet", "Java", "21 (LTS)"],
      ["Framework backend", "Spring Boot (Web, Data JPA, Security, Validation, Actuator)", "3.3.4"],
      ["Sécurité", "Spring Security + JWT (jjwt)", "0.12.6"],
      ["Persistance", "Spring Data JPA / Hibernate", "Spring Boot 3.3"],
      ["Migrations BD", "Flyway", "Spring Boot 3.3"],
      ["Base de données", "PostgreSQL (H2 en test)", "—"],
      ["Documentation API", "springdoc-openapi (Swagger UI)", "2.6.0"],
      ["Exports", "Apache POI (Excel), OpenPDF (reçus PDF), PDFBox", "5.3 / 1.3 / 3.0"],
      ["Client guichet", "JavaFX (controls, fxml, graphics, swing)", "21.0.5"],
      ["Icônes guichet", "Ikonli FontAwesome 5", "12.3.1"],
      ["JSON guichet", "Jackson", "2.17.2"],
      ["Packaging guichet", "Maven Shade + jlink + jpackage (WiX)", "—"],
      ["Application web", "Angular + Angular Material + TypeScript", "17 / 5.4"],
      ["Build", "Maven (backend, guichet) / npm (web)", "—"],
    ],
    [2700, 4860, 1800]
  ));

  // 3. Backend
  c.push(new Paragraph({ children: [new PageBreak()] }));
  c.push(H1("3. Le serveur central (backend Spring Boot)"));
  c.push(P("Le backend est le cœur du système. Il expose une API REST documentée et applique toutes les règles métier et de sécurité."));

  c.push(H2("3.1. Découpage en couches"));
  c.push(bullet([bold("controller"), run(" : points d'entrée REST (auth, caisses, opérations, versements, clients, banques, catégories, journaux, supervision, reporting, audit, paramètres reçu, timbre, sauvegarde).")]));
  c.push(bullet([bold("service"), run(" : logique métier (ouverture/clôture, calcul des écarts, audit, génération PDF…).")]));
  c.push(bullet([bold("repository"), run(" : accès aux données via Spring Data JPA.")]));
  c.push(bullet([bold("model / dto"), run(" : entités persistées et objets de transfert exposés à l'API.")]));
  c.push(bullet([bold("config / audit"), run(" : sécurité, OpenAPI, initialisation des données, journal d'audit.")]));

  c.push(H2("3.2. Sécurité"));
  c.push(P([run("L'authentification est "), bold("sans état (stateless)"), run(" : à la connexion, le serveur émet un "), bold("JWT"), run(" (valable 24 h) que le client renvoie à chaque appel. L'autorisation est "), bold("basée sur les rôles"), run(" : chaque endpoint est protégé par des règles (annotations @PreAuthorize / configuration Spring Security). Les mots de passe sont stockés hachés. Un journal d'audit trace les actions sensibles.")]));

  c.push(H2("3.3. Persistance & migrations"));
  c.push(P([run("La persistance repose sur "), bold("JPA/Hibernate"), run(". Le schéma n'est PAS généré par Hibernate (ddl-auto=validate) : il est piloté par "), bold("Flyway"), run(", qui applique des scripts de migration versionnés (V1 → V12). Cela garantit un schéma reproductible et traçable entre les environnements.")]));

  c.push(H2("3.4. Services transverses"));
  c.push(bullet([bold("OpenAPI / Swagger UI"), run(" : documentation interactive de l'API.")]));
  c.push(bullet([bold("Apache POI"), run(" : export Excel des journaux.")]));
  c.push(bullet([bold("OpenPDF"), run(" : génération des reçus PDF ; PDFBox pour l'aperçu image côté admin.")]));
  c.push(bullet([bold("Intégration WhatsApp"), run(" (API Meta Graph) : envoi du reçu au client (activable).")]));
  c.push(bullet([bold("Spring Boot Actuator"), run(" : points de santé/supervision du serveur.")]));
  c.push(bullet([bold("Fuseau Africa/Dakar"), run(" et dates ISO pour des horodatages cohérents.")]));

  // 4. Guichet
  c.push(H1("4. Le client lourd Guichet (JavaFX)"));
  c.push(H2("4.1. Pourquoi un client lourd"));
  c.push(P("Le poste de caisse a des besoins qu'un simple navigateur satisfait mal : impression fiable des reçus sur l'imprimante locale, ergonomie clavier rapide, fenêtres natives, et fonctionnement stable au comptoir. Un client de bureau JavaFX répond à ces contraintes tout en restant pilotable par le serveur central."));
  c.push(H2("4.2. Structure interne"));
  c.push(bullet([bold("Vues FXML + CSS"), run(" (modèle MVC) : écrans login, sélection de caisse, tableau de bord, opération, versement.")]));
  c.push(bullet([bold("Controllers"), run(" : logique d'écran (CaissierController, NouvelleOperationController, VersementController…).")]));
  c.push(bullet([bold("api"), run(" : client HTTP vers le backend (Jackson pour le JSON).")]));
  c.push(bullet([bold("util"), run(" : thème clair/sombre, exécution asynchrone, boîtes de dialogue personnalisées (RtsDialog), session.")]));
  c.push(bullet([bold("print"), run(" : impression et export du reçu.")]));
  c.push(H2("4.3. Packaging & distribution"));
  c.push(P([run("La chaîne de packaging produit un installateur "), bold("MSI"), run(" autonome : "), bold("Maven Shade"), run(" assemble un fat-JAR (toutes dépendances incluses), "), bold("jlink"), run(" construit un runtime Java minimal (avec les modules JavaFX), puis "), bold("jpackage"), run(" (via WiX) génère le .msi. L'utilisateur final n'a donc "), bold("aucun Java à installer"), run(".")]));

  // 5. Web
  c.push(H1("5. L'application Web d'administration (Angular)"));
  c.push(P([run("L'administration et la supervision passent par une "), bold("SPA Angular 17"), run(" habillée avec "), bold("Angular Material"), run(". Le web est le bon outil pour ces usages : accès depuis n'importe quel poste sans installation, tableaux de bord riches, gestion des référentiels, et déploiement centralisé des mises à jour.")]));
  c.push(P("Les modules couvrent : tableau de bord, supervision, utilisateurs, caisses, banques, produits, clients, journaux, opérations, versements, paramètres du reçu, timbre, audit et sauvegarde."));

  // 6. Base de données
  c.push(H1("6. Le modèle de données (PostgreSQL)"));
  c.push(P("Les données sont centralisées dans une base relationnelle. Tables principales :"));
  c.push(makeTable(
    ["Table", "Contenu"],
    [
      ["utilisateurs", "Comptes et rôles (ADMIN, SUPERVISEUR, CAISSIER, AGENT_RECETTE, CONTROLEUR)."],
      ["caisses", "Caisses physiques, statut, caissier affecté, types d'opération autorisés."],
      ["journaux_caisse", "Journées de caisse : ouverture, fond, clôture, soldes, écart."],
      ["operations_caisse", "Encaissements/décaissements : produit, montant HT, timbre, TTC, mode de paiement, reçu."],
      ["versements", "Versements d'espèces en banque (banque, montant, bordereau)."],
      ["clients", "Référentiel clients (raison sociale, contact, WhatsApp)."],
      ["banque", "Référentiel des banques."],
      ["categories_operation", "Produits / catégories encaissables."],
      ["audit_logs", "Journal d'audit des actions sensibles."],
    ],
    [2500, 6860]
  ));
  c.push(P([run("Le schéma est créé et maintenu par "), bold("12 migrations Flyway"), run(" (V1 schéma initial, puis index/contraintes, paramètres du reçu, logo, timbre, rôles AGENT_RECETTE et CONTROLEUR, types d'opération par caisse, etc.).")]));

  // 7. Sécurité transversale
  c.push(H1("7. Sécurité transversale"));
  c.push(bullet([bold("Authentification JWT"), run(" stateless, jeton de 24 h, émetteur RTS-Caisse.")]));
  c.push(bullet([bold("Autorisation par rôle"), run(" sur chaque endpoint ; le rôle CONTROLEUR est strictement en lecture seule.")]));
  c.push(bullet([bold("Mots de passe hachés"), run(" et compte super-admin protégé.")]));
  c.push(bullet([bold("Journal d'audit"), run(" horodaté pour la traçabilité et le contrôle interne.")]));
  c.push(bullet([bold("Validation des entrées"), run(" (Bean Validation) et limites de taille des pièces jointes (10 Mo).")]));

  // 8. Justification des choix (LE cœur du document)
  c.push(new Paragraph({ children: [new PageBreak()] }));
  c.push(H1("8. Justification des choix technologiques"));
  c.push(P("Chaque technologie a été retenue pour répondre à un besoin précis du projet. Le tableau ci-dessous résume les arbitrages."));
  c.push(makeTable(
    ["Besoin", "Choix retenu", "Pourquoi (et alternatives écartées)"],
    [
      ["Plateforme serveur robuste et productive",
       "Spring Boot 3 / Java 21",
       "Écosystème mature, sécurité, JPA, validation et actuator intégrés ; Java 21 LTS partagé avec le guichet. Alternatives : Node/Express (moins structurant pour le métier transactionnel), .NET (hors compétences de l'équipe)."],
      ["Données transactionnelles fiables",
       "PostgreSQL",
       "SGBD relationnel open-source robuste, transactions ACID, adapté à la comptabilité de caisse. H2 sert uniquement aux tests. Alternative : MySQL (moins riche), NoSQL (inadapté aux écritures comptables)."],
      ["Schéma reproductible et versionné",
       "Flyway (ddl-auto=validate)",
       "Migrations SQL versionnées et rejouables ; le schéma n'est jamais « deviné » par Hibernate. Alternative : génération auto Hibernate (risquée en production)."],
      ["Authentification multi-clients",
       "Spring Security + JWT",
       "Jeton stateless idéal pour deux clients hétérogènes (desktop + web) sans session serveur. Alternative : sessions/cookies (couplant, moins adapté au client lourd)."],
      ["Poste de caisse fiable (impression, ergonomie)",
       "Client lourd JavaFX",
       "Impression locale des reçus, fenêtres natives, saisie rapide, stabilité au comptoir. Alternative : tout-web (impression et ergonomie de caisse moins maîtrisées)."],
      ["Déploiement du guichet sans prérequis",
       "jlink + jpackage (MSI)",
       "Installateur Windows autonome embarquant son runtime Java : rien à installer côté poste. Alternative : exiger un JRE sur chaque poste (fragile, coûteux à maintenir)."],
      ["Administration accessible partout",
       "Angular 17 + Material",
       "SPA installée nulle part, mises à jour centralisées, composants UI riches pour dashboards et référentiels. Alternative : multiplier les écrans dans le client lourd (déploiement lourd)."],
      ["Documents officiels",
       "OpenPDF (reçus) / Apache POI (Excel)",
       "Génération native de reçus PDF et d'exports Excel des journaux, sans dépendance bureautique externe."],
      ["Notification client",
       "API WhatsApp (Meta)",
       "Canal largement utilisé au Sénégal pour transmettre le reçu ; activable/désactivable selon le déploiement."],
      ["Traçabilité & contrôle",
       "Journal d'audit + rôle CONTROLEUR",
       "Conformité au besoin de contrôle interne : historique des actions et accès lecture seule dédié."],
    ],
    [2100, 1900, 5360]
  ));

  c.push(H2("8.1. Pourquoi deux clients plutôt qu'un seul ?"));
  c.push(P("Les deux populations d'utilisateurs ont des besoins opposés. Le caissier a besoin d'un poste fixe, rapide au clavier et fiable pour imprimer : un client lourd est optimal. L'administrateur/superviseur a besoin de mobilité et de tableaux de bord : le web est optimal. Mutualiser le métier et la sécurité dans un seul backend permet d'offrir l'interface la mieux adaptée à chaque rôle, sans dupliquer les règles de gestion."));

  // 9. Déploiement
  c.push(H1("9. Déploiement & exécution"));
  c.push(bullet([bold("Backend"), run(" : application Spring Boot (JAR exécutable) connectée à PostgreSQL ; Flyway applique les migrations au démarrage. Port 9090, profils dev/docker.")]));
  c.push(bullet([bold("Base de données"), run(" : instance PostgreSQL dédiée (sauvegardes via le module Sauvegarde).")]));
  c.push(bullet([bold("Client guichet"), run(" : MSI installé sur chaque poste caissier (construit via build-msi.ps1).")]));
  c.push(bullet([bold("Application web"), run(" : build Angular servi statiquement, pointant vers l'API.")]));

  // 10. Évolutions
  c.push(H1("10. Évolutions possibles"));
  c.push(bullet("Mode hors-ligne du guichet avec synchronisation différée."));
  c.push(bullet("Authentification renforcée (2FA) pour les rôles sensibles."));
  c.push(bullet("Tableaux de bord analytiques avancés et reporting consolidé multi-sites."));
  c.push(bullet("Conteneurisation complète (Docker) et intégration continue."));
  c.push(spacer());
  c.push(P([new TextRun({ text: "Document technique de référence du projet RTS Caisse. À tenir à jour à chaque évolution majeure de la pile ou du modèle de données.", italics: true, color: GREY })]));

  return buildDoc("Architecture & choix techniques", c);
}

// ---------- génération ----------
(async () => {
  const docs = [
    [manuel(), "Manuel_Utilisation_RTS_Caisse.docx"],
    [architecture(), "Architecture_et_Choix_Techniques_RTS_Caisse.docx"],
  ];
  for (const [doc, name] of docs) {
    ordCounter = 0; // reset (au cas où)
    const buf = await Packer.toBuffer(doc);
    fs.writeFileSync(path.join(OUT_DIR, name), buf);
    console.log("OK -> " + name + " (" + (buf.length / 1024).toFixed(0) + " Ko)");
  }
})();

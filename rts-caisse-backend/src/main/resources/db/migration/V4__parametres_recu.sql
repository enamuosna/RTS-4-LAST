-- =====================================================================
-- V4 — Paramètres de personnalisation du reçu PDF
-- Table singleton (id=1) modifiable uniquement par les ADMIN.
-- =====================================================================

CREATE TABLE IF NOT EXISTS parametres_recu (
    id                          BIGINT PRIMARY KEY,

    -- En-tête
    logo_texte                  VARCHAR(40),
    raison_sociale              VARCHAR(200),
    sous_titre_entete           VARCHAR(200),
    ligne_legale                VARCHAR(200),
    capital                     VARCHAR(100),
    adresse                     VARCHAR(200),
    telephone                   VARCHAR(60),
    boite_postale               VARCHAR(60),
    ninea                       VARCHAR(60),

    -- Footer
    footer_ligne1               VARCHAR(200),
    footer_ligne2               VARCHAR(200),
    ville_signature             VARCHAR(100),

    -- Couleurs (hex #RRGGBB)
    couleur_primaire            VARCHAR(7),
    couleur_accent              VARCHAR(7),
    couleur_texte               VARCHAR(7),
    couleur_texte_secondaire    VARCHAR(7),
    couleur_success             VARCHAR(7),
    couleur_danger              VARCHAR(7),
    couleur_fond_montant        VARCHAR(7),

    -- Tailles de police (pt)
    taille_titre                INT,
    taille_entete               INT,
    taille_corps                INT,
    taille_montant              INT,
    taille_footer               INT,

    -- Layout : ordre + visibilité des sections (JSON)
    layout_json                 TEXT,

    -- Métadonnées
    updated_at                  TIMESTAMP,
    updated_by                  VARCHAR(80)
);

-- =====================================================================
-- Insertion de la ligne unique avec les valeurs par défaut RTS.
-- Ces valeurs reproduisent l'apparence actuelle codée en dur dans
-- RecuPdfService — un ADMIN peut ensuite les modifier via l'IHM.
-- =====================================================================

INSERT INTO parametres_recu (
    id, logo_texte, raison_sociale, sous_titre_entete, ligne_legale,
    capital, adresse, telephone, boite_postale, ninea,
    footer_ligne1, footer_ligne2, ville_signature,
    couleur_primaire, couleur_accent, couleur_texte, couleur_texte_secondaire,
    couleur_success, couleur_danger, couleur_fond_montant,
    taille_titre, taille_entete, taille_corps, taille_montant, taille_footer,
    layout_json, updated_at, updated_by
) VALUES (
    1,
    'RTS',
    'SOCIÉTÉ NATIONALE DE RADIODIFFUSION TÉLÉVISION DU SÉNÉGAL',
    'Radiodiffusion Télévision Sénégalaise',
    'Créée par la loi n° 92-02 du 06 janvier 1992',
    'Capital : 7 milliards FCFA',
    'Triangle Sud',
    'Tél. (221) 33 849 12 12',
    'B.P. 1765 — DAKAR',
    'NINEA : 2059782 2G3',
    'Merci de votre passage.',
    'RTS — Conservez ce reçu comme preuve.',
    'Dakar',
    '#E30613', '#1A1A1A', '#212121', '#9E9E9E',
    '#2E7D32', '#C62828', '#FBE5E7',
    14, 16, 9, 20, 7,
    '[{"id":"header","visible":true},{"id":"titre","visible":true},{"id":"numero","visible":true},{"id":"details","visible":true},{"id":"client","visible":true},{"id":"montant","visible":true},{"id":"motif","visible":true},{"id":"annulation","visible":true},{"id":"signature","visible":true},{"id":"footer","visible":true}]',
    NOW(),
    'system'
) ON CONFLICT (id) DO NOTHING;

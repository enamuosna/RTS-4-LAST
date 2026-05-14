-- =====================================================================
-- V5 — Ajout du logo image (binaire) sur les paramètres du reçu
-- L'admin peut désormais uploader un fichier image (PNG, JPG, SVG…)
-- au lieu d'utiliser le simple texte "RTS". Le texte reste comme
-- fallback si aucune image n'a été déposée.
-- =====================================================================

ALTER TABLE parametres_recu
    ADD COLUMN IF NOT EXISTS logo_image        BYTEA,
    ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR(80);

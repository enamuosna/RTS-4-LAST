-- =====================================================================
--  V12 — Configuration personnalisable du timbre fiscal
--
--  Table singleton (id=1) modifiable uniquement par les ADMIN via
--  /api/parametres/timbre. Le guichet lit cette config pour reproduire
--  exactement le calcul fait — et autoritatif — côté backend.
--
--  Le seed reproduit le comportement historique codé en dur :
--    - actif, seuil 20 000 FCFA, taux 1%
--    - modes concernés : ESPECES uniquement
--    - catégories concernées : [] = toutes
--  Un ADMIN peut ensuite tout personnaliser depuis l'IHM web.
--
--  IF NOT EXISTS / ON CONFLICT : idempotent (et compatible avec le profil
--  docker où Hibernate ddl-auto=update a déjà pu créer la table, le seed
--  étant alors assuré par DataInitializer).
-- =====================================================================

CREATE TABLE IF NOT EXISTS timbre_config (
    id                    BIGINT PRIMARY KEY,
    actif                 BOOLEAN        NOT NULL DEFAULT TRUE,
    seuil                 NUMERIC(15, 2) NOT NULL DEFAULT 20000,
    pourcentage           NUMERIC(5, 2)  NOT NULL DEFAULT 1.00,
    categories_json       TEXT,
    modes_paiement_json   TEXT,
    updated_at            TIMESTAMP,
    updated_by            VARCHAR(80)
);

INSERT INTO timbre_config (
    id, actif, seuil, pourcentage, categories_json, modes_paiement_json,
    updated_at, updated_by
) VALUES (
    1, TRUE, 20000, 1.00, '[]', '["ESPECES"]', NOW(), 'system'
) ON CONFLICT (id) DO NOTHING;

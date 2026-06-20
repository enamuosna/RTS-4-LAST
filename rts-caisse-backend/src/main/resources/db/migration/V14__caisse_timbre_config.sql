-- =====================================================================
--  V14 — Configuration du timbre PAR CAISSE (+ mode AUTO / MANUEL)
--
--  Chaque caisse peut avoir sa propre config timbre. Une caisse sans
--  ligne utilise les valeurs par défaut (mode AUTO, seuil 20 000, 1%,
--  ESPECES) côté service. En mode MANUEL, le calcul auto est désactivé
--  pour la caisse : le caissier saisit le timbre (optionnel).
-- =====================================================================

CREATE TABLE IF NOT EXISTS caisse_timbre_config (
    id                   BIGSERIAL     PRIMARY KEY,
    caisse_id            BIGINT        NOT NULL,
    actif                BOOLEAN       NOT NULL DEFAULT TRUE,
    seuil                NUMERIC(15, 2) NOT NULL DEFAULT 20000,
    pourcentage          NUMERIC(5, 2)  NOT NULL DEFAULT 1.00,
    categories_json      TEXT,
    modes_paiement_json  TEXT,
    mode                 VARCHAR(10)   NOT NULL DEFAULT 'AUTO',
    updated_at           TIMESTAMP,
    updated_by           VARCHAR(80),
    CONSTRAINT uk_caisse_timbre_caisse UNIQUE (caisse_id),
    CONSTRAINT fk_caisse_timbre_caisse FOREIGN KEY (caisse_id) REFERENCES caisses(id)
);

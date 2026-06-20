-- =====================================================================
--  V15 — Diffusions multiples (date/heure/langue) + référentiel Langues
--
--  - langues : référentiel des langues de diffusion (FR, WO, Pulaar…).
--  - operation_diffusion : créneaux de diffusion d'une opération
--    (plusieurs jours/heures possibles, langue optionnelle).
--  - categories_operation.propose_langue : indique si le produit propose
--    le choix d'une langue de diffusion (ex. Avis & Communiqués).
-- =====================================================================

CREATE TABLE IF NOT EXISTS langues (
    id       BIGSERIAL    PRIMARY KEY,
    code     VARCHAR(20)  NOT NULL,
    libelle  VARCHAR(60)  NOT NULL,
    actif    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_langue_code UNIQUE (code)
);

INSERT INTO langues (code, libelle, actif) VALUES
    ('FR',  'Français',  TRUE),
    ('WO',  'Wolof',     TRUE),
    ('FF',  'Pulaar',    TRUE),
    ('MND', 'Mandingue', TRUE),
    ('SRR', 'Sérère',    TRUE),
    ('DYO', 'Diola',     TRUE)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS operation_diffusion (
    id              BIGSERIAL    PRIMARY KEY,
    operation_id    BIGINT       NOT NULL,
    date_heure      TIMESTAMP    NOT NULL,
    langue_id       BIGINT,
    langue_libelle  VARCHAR(60),
    CONSTRAINT fk_diffusion_operation FOREIGN KEY (operation_id)
        REFERENCES operations_caisse(id) ON DELETE CASCADE,
    CONSTRAINT fk_diffusion_langue FOREIGN KEY (langue_id) REFERENCES langues(id)
);

CREATE INDEX IF NOT EXISTS idx_diffusion_operation ON operation_diffusion (operation_id);

ALTER TABLE categories_operation
    ADD COLUMN IF NOT EXISTS propose_langue BOOLEAN NOT NULL DEFAULT FALSE;

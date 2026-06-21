-- =====================================================================
--  V16 — Nombre de passages à l'antenne (informatif)
--
--  - operations_caisse.nombre_passages : nombre de passages à l'antenne
--    prévus (un spot diffusé N fois). Purement informatif, optionnel.
--  - categories_operation.propose_nombre_passages : indique si le produit
--    propose la saisie de ce nombre au guichet (ex. spots publicitaires).
--
--  En profil docker, Flyway est désactivé (ddl-auto=update + @ColumnDefault
--  gèrent l'ajout). Cette migration sert aux environnements Flyway.
-- =====================================================================

ALTER TABLE operations_caisse
    ADD COLUMN IF NOT EXISTS nombre_passages INTEGER;

ALTER TABLE categories_operation
    ADD COLUMN IF NOT EXISTS propose_nombre_passages BOOLEAN NOT NULL DEFAULT FALSE;

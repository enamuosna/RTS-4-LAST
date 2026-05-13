-- ============================================================
--  V3 : motif d'opération devient optionnel
-- ============================================================
--  Le formulaire guichet a retiré le champ motif depuis la v5 du
--  FXML. Pour aligner la persistance, on lève la contrainte NOT
--  NULL et on autorise jusqu'à 500 caractères (cohérence avec la
--  taille déclarée dans le DTO OperationCaisseRequest).
-- ============================================================

ALTER TABLE operations_caisse
    ALTER COLUMN motif DROP NOT NULL;

ALTER TABLE operations_caisse
    ALTER COLUMN motif TYPE VARCHAR(500);

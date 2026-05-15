-- =====================================================================
-- V6 — Ajout du timbre fiscal et du montant TTC sur les opérations
--
-- Le caissier saisit montant (HT) + timbre (taxe optionnelle).
-- Le montant_ttc est calculé à l'enregistrement = montant + timbre.
--
-- IMPORTANT : on procède en 3 étapes pour éviter le piège
-- « column contains null values » sur les anciennes lignes :
--   1) ADD COLUMN nullable
--   2) UPDATE des anciennes valeurs (timbre=0, montant_ttc=montant)
--   3) SET NOT NULL + SET DEFAULT
-- =====================================================================

-- ---------- TIMBRE ----------
ALTER TABLE operations_caisse
    ADD COLUMN IF NOT EXISTS timbre NUMERIC(15, 2);

UPDATE operations_caisse
   SET timbre = 0
 WHERE timbre IS NULL;

ALTER TABLE operations_caisse
    ALTER COLUMN timbre SET NOT NULL,
    ALTER COLUMN timbre SET DEFAULT 0;

-- ---------- MONTANT TTC ----------
ALTER TABLE operations_caisse
    ADD COLUMN IF NOT EXISTS montant_ttc NUMERIC(15, 2);

-- Pour les anciennes opérations, montant_ttc = montant (timbre=0)
UPDATE operations_caisse
   SET montant_ttc = montant
 WHERE montant_ttc IS NULL;

ALTER TABLE operations_caisse
    ALTER COLUMN montant_ttc SET NOT NULL;

-- =====================================================================
-- V6 — Ajout du timbre fiscal et du montant TTC sur les opérations
--
-- Le caissier saisit montant (HT) + timbre (taxe optionnelle).
-- Le montant_ttc est calculé à l'enregistrement = montant + timbre.
-- Pour les opérations existantes, on initialise montant_ttc = montant
-- et timbre = 0.
-- =====================================================================

ALTER TABLE operations_caisse
    ADD COLUMN IF NOT EXISTS timbre       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS montant_ttc  NUMERIC(15, 2);

-- Initialise montant_ttc pour les anciennes opérations (= montant car timbre=0)
UPDATE operations_caisse
   SET montant_ttc = montant
 WHERE montant_ttc IS NULL;

-- Une fois rempli, on rend la colonne NOT NULL
ALTER TABLE operations_caisse
    ALTER COLUMN montant_ttc SET NOT NULL;

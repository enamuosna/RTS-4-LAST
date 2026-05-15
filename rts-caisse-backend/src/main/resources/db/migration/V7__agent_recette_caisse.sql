-- =====================================================================
-- V7 — Ajout de l'agent de recette sur la table caisses
--
-- Chaque caisse peut être affectée à un agent de recette (utilisateur
-- de rôle AGENT_RECETTE). Cet agent peut modifier et réactiver les
-- opérations de la caisse en cas d'erreur du caissier.
--
-- Le champ est nullable : une caisse sans agent de recette continue
-- de fonctionner normalement (modification réservée aux ADMIN/SUPERVISEUR).
-- =====================================================================

ALTER TABLE caisses
    ADD COLUMN IF NOT EXISTS agent_recette_id BIGINT;

-- PostgreSQL ne supporte pas ADD CONSTRAINT IF NOT EXISTS, on encapsule
-- dans un bloc DO pour rester idempotent.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'caisses'
          AND constraint_name = 'fk_caisses_agent_recette'
    ) THEN
        ALTER TABLE caisses
            ADD CONSTRAINT fk_caisses_agent_recette
            FOREIGN KEY (agent_recette_id) REFERENCES utilisateurs(id);
    END IF;
END $$;

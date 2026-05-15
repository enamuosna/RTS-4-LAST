-- =====================================================================
-- V8 — Met à jour la CHECK constraint sur utilisateurs.role pour
-- inclure le nouveau rôle AGENT_RECETTE.
--
-- Hibernate auto-DDL (mode update) crée une CHECK constraint quand
-- on map un Enum, mais ne la met JAMAIS à jour quand on ajoute des
-- valeurs à l'enum. Resultat : impossible de creer un AGENT_RECETTE
-- car PostgreSQL rejette la nouvelle valeur :
--   ERROR: new row for relation "utilisateurs" violates check
--   constraint "utilisateurs_role_check"
--
-- On droppe la contrainte existante et on la recrée avec les 4 valeurs.
-- =====================================================================

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- Cherche la CHECK constraint sur la colonne role (nom dépend de l'historique)
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'utilisateurs'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%role%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE utilisateurs DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END $$;

-- Recrée la contrainte avec les 4 valeurs autorisées
ALTER TABLE utilisateurs
    ADD CONSTRAINT utilisateurs_role_check
    CHECK (role IN ('ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE'));

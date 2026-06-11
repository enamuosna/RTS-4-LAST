-- =====================================================================
--  V10 - Ajout du rôle CONTROLEUR (acteur en lecture seule)
--
--  Contexte
--  --------
--  La colonne utilisateurs.role a porté, selon l'historique de la base,
--  une CHECK constraint énumérant explicitement les rôles connus :
--    - V1  : chk_utilisateur_role   ('ADMIN','SUPERVISEUR','CAISSIER')
--    - V8  : utilisateurs_role_check ('ADMIN','SUPERVISEUR','CAISSIER','AGENT_RECETTE')
--    - prod (ddl-auto=update) : Hibernate a pu en générer une au 1er démarrage.
--  Ajouter une nouvelle valeur à l'enum Java (CONTROLEUR) sans toucher ce
--  CHECK fait échouer tout INSERT/UPDATE :
--    ERROR: new row for relation "utilisateurs" violates check
--           constraint "utilisateurs_role_check"
--
--  Solution
--  --------
--  On drop TOUTE CHECK constraint portant sur la colonne role, une bonne
--  fois pour toutes (même philosophie que V9 sur audit_logs.action). La
--  validation des valeurs reste garantie côté Java par
--  @Enumerated(EnumType.STRING) : Hibernate refuse d'envoyer en base une
--  valeur absente de l'enum. On évite ainsi une migration manuelle à
--  chaque nouveau rôle.
--
--  Côté entité, Utilisateur.role utilise désormais un columnDefinition
--  explicite (VARCHAR(20) NOT NULL) pour empêcher Hibernate de regénérer
--  ce CHECK au démarrage en profil docker (ddl-auto=update, Flyway off).
--
--  Le nom de la contrainte dépend de l'historique : on boucle sur toutes
--  les CHECK constraints de la table dont la définition référence "role".
-- =====================================================================

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'utilisateurs'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%role%'
    LOOP
        EXECUTE 'ALTER TABLE utilisateurs DROP CONSTRAINT ' || quote_ident(c.conname);
    END LOOP;
END $$;

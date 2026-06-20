-- =====================================================================
--  V11 - Type d'opération autorisé par caisse
--
--  L'ADMIN précise, à la création d'une caisse, si elle effectue des
--  encaissements (ENTREE), des décaissements (SORTIE) ou les deux (TOUS).
--  Le guichet adapte alors l'affichage du type d'opération pour le
--  caissier (verrouillé sur le type choisi si un seul est autorisé).
--
--  La colonne est NOT NULL avec DEFAULT 'TOUS' : les caisses déjà en base
--  deviennent automatiquement mixtes (comportement inchangé). La
--  validation des valeurs reste assurée côté Java par
--  @Enumerated(EnumType.STRING) ; on ne pose pas de CHECK afin de pouvoir
--  faire évoluer l'enum sans migration (cf. V9/V10).
--
--  IF NOT EXISTS : idempotent si Hibernate (profil docker, ddl-auto=update)
--  a déjà créé la colonne au démarrage.
-- =====================================================================

ALTER TABLE caisses
    ADD COLUMN IF NOT EXISTS type_operation_autorise VARCHAR(20) NOT NULL DEFAULT 'TOUS';

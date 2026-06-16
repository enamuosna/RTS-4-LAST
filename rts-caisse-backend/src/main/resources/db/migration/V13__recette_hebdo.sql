-- =====================================================================
--  V13 — Ventilation hebdomadaire des recettes + double validation
--
--  Persiste UNIQUEMENT l'état de validation d'une ventilation
--  hebdomadaire (par caisse et période). Les montants (par produit,
--  totaux, fiches de références, reversements) restent calculés à la
--  volée depuis operations_caisse / versements.
--
--  Double validation reproduisant la fiche papier RTS :
--    - Contrôle 1 : Chef Unité Finances (role CHEF_UNITE_FINANCES)
--    - Contrôle 2 : Chef de Département (role CHEF_DEPARTEMENT)
--
--  NB : aucun ALTER sur utilisateurs.role n'est nécessaire — la CHECK
--  constraint a été supprimée définitivement en V10 ; les nouveaux
--  rôles sont validés côté Java par l'enum (VARCHAR(20)).
-- =====================================================================

CREATE TABLE IF NOT EXISTS recette_hebdo (
    id                BIGSERIAL     PRIMARY KEY,
    caisse_id         BIGINT        NOT NULL,
    date_debut        DATE          NOT NULL,
    date_fin          DATE          NOT NULL,
    statut            VARCHAR(20)   NOT NULL DEFAULT 'BROUILLON',
    controle1_par_id  BIGINT,
    controle1_le      TIMESTAMP,
    controle2_par_id  BIGINT,
    controle2_le      TIMESTAMP,
    commentaire       VARCHAR(500),
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_recette_hebdo_periode UNIQUE (caisse_id, date_debut, date_fin),
    CONSTRAINT fk_recette_caisse    FOREIGN KEY (caisse_id)        REFERENCES caisses(id),
    CONSTRAINT fk_recette_controle1 FOREIGN KEY (controle1_par_id) REFERENCES utilisateurs(id),
    CONSTRAINT fk_recette_controle2 FOREIGN KEY (controle2_par_id) REFERENCES utilisateurs(id)
);

CREATE INDEX IF NOT EXISTS idx_recette_hebdo_caisse ON recette_hebdo (caisse_id, date_debut);

-- ============================================================
--  RTS CAISSE - Schéma initial (baseline)
--  Reflète l'état des entités JPA : utilisateurs, caisses,
--  catégories, clients, banques, journaux, opérations, audit.
-- ============================================================
--  Note : si la base contient déjà ce schéma (créé par
--  ddl-auto=update en docker), Flyway pose un repère
--  baseline et ne rejoue PAS ce script (baseline-on-migrate=true).
-- ============================================================

-- ========== UTILISATEURS ==========
CREATE TABLE IF NOT EXISTS utilisateurs (
    id             BIGSERIAL PRIMARY KEY,
    matricule      VARCHAR(30)  NOT NULL,
    login          VARCHAR(50)  NOT NULL,
    mot_de_passe   VARCHAR(255) NOT NULL,
    prenom         VARCHAR(80)  NOT NULL,
    nom            VARCHAR(80)  NOT NULL,
    email          VARCHAR(150),
    telephone      VARCHAR(20),
    role           VARCHAR(20)  NOT NULL,
    actif          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    CONSTRAINT uk_utilisateur_login     UNIQUE (login),
    CONSTRAINT uk_utilisateur_matricule UNIQUE (matricule),
    CONSTRAINT chk_utilisateur_role     CHECK (role IN ('ADMIN', 'SUPERVISEUR', 'CAISSIER'))
);

-- ========== CAISSES ==========
CREATE TABLE IF NOT EXISTS caisses (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)     NOT NULL,
    libelle         VARCHAR(100)    NOT NULL,
    emplacement     VARCHAR(150),
    statut          VARCHAR(20)     NOT NULL DEFAULT 'FERMEE',
    solde_courant   NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    caissier_id     BIGINT,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_caisse_code     UNIQUE (code),
    CONSTRAINT fk_caisse_caissier FOREIGN KEY (caissier_id) REFERENCES utilisateurs(id),
    CONSTRAINT chk_caisse_statut  CHECK (statut IN ('FERMEE', 'OUVERTE', 'SUSPENDUE'))
);

-- ========== CATEGORIES D'OPERATION ==========
CREATE TABLE IF NOT EXISTS categories_operation (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL,
    libelle         VARCHAR(120) NOT NULL,
    type_operation  VARCHAR(10)  NOT NULL,
    actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_categorie_code  UNIQUE (code),
    CONSTRAINT chk_categorie_type CHECK (type_operation IN ('ENTREE', 'SORTIE'))
);

-- ========== CLIENTS ==========
CREATE TABLE IF NOT EXISTS clients (
    id                  BIGSERIAL PRIMARY KEY,
    raison_sociale      VARCHAR(150) NOT NULL,
    identifiant_fiscal  VARCHAR(50),
    telephone           VARCHAR(20),
    email               VARCHAR(150),
    adresse             VARCHAR(255),
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP
);

-- ========== BANQUES ==========
-- (entité Banque : table autonome, créé/maj horodatés via @PrePersist/@PreUpdate)
CREATE TABLE IF NOT EXISTS banque (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(10)  NOT NULL,
    libelle             VARCHAR(200) NOT NULL,
    pays                VARCHAR(80)  NOT NULL DEFAULT 'SÉNÉGAL',
    code_etablissement  VARCHAR(20),
    site_internet       VARCHAR(255),
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    CONSTRAINT uk_banque_code UNIQUE (code)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_banque_code  ON banque (code);
CREATE INDEX        IF NOT EXISTS idx_banque_actif ON banque (actif);

-- ========== JOURNAUX DE CAISSE ==========
CREATE TABLE IF NOT EXISTS journaux_caisse (
    id                BIGSERIAL PRIMARY KEY,
    date_journal      DATE            NOT NULL,
    caisse_id         BIGINT          NOT NULL,
    caissier_id       BIGINT          NOT NULL,
    fond_ouverture    NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    total_entrees     NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    total_sorties     NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    solde_theorique   NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    solde_reel        NUMERIC(15, 2),
    ecart             NUMERIC(15, 2),
    commentaire       VARCHAR(500),
    ouvert_le         TIMESTAMP       NOT NULL,
    cloture_le        TIMESTAMP,
    valide_par_id     BIGINT,
    cloture           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP       NOT NULL,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_journal_caisse_date UNIQUE (caisse_id, date_journal),
    CONSTRAINT fk_journal_caisse     FOREIGN KEY (caisse_id)     REFERENCES caisses(id),
    CONSTRAINT fk_journal_caissier   FOREIGN KEY (caissier_id)   REFERENCES utilisateurs(id),
    CONSTRAINT fk_journal_validateur FOREIGN KEY (valide_par_id) REFERENCES utilisateurs(id)
);

-- ========== OPERATIONS DE CAISSE ==========
CREATE TABLE IF NOT EXISTS operations_caisse (
    id                  BIGSERIAL PRIMARY KEY,
    numero_recu         VARCHAR(50)    NOT NULL,
    type_operation      VARCHAR(10)    NOT NULL,
    montant             NUMERIC(15, 2) NOT NULL,
    motif               VARCHAR(255)   NOT NULL,
    mode_paiement       VARCHAR(20)    NOT NULL,
    reference           VARCHAR(100),
    date_operation      TIMESTAMP      NOT NULL,
    caisse_id           BIGINT         NOT NULL,
    caissier_id         BIGINT         NOT NULL,
    categorie_id        BIGINT         NOT NULL,
    client_id           BIGINT,
    journal_id          BIGINT,
    banque_id           BIGINT,
    annulee             BOOLEAN        NOT NULL DEFAULT FALSE,
    motif_annulation    VARCHAR(255),
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    CONSTRAINT uk_operation_numero_recu UNIQUE (numero_recu),
    CONSTRAINT fk_operation_caisse    FOREIGN KEY (caisse_id)    REFERENCES caisses(id),
    CONSTRAINT fk_operation_caissier  FOREIGN KEY (caissier_id)  REFERENCES utilisateurs(id),
    CONSTRAINT fk_operation_categorie FOREIGN KEY (categorie_id) REFERENCES categories_operation(id),
    CONSTRAINT fk_operation_client    FOREIGN KEY (client_id)    REFERENCES clients(id),
    CONSTRAINT fk_operation_journal   FOREIGN KEY (journal_id)   REFERENCES journaux_caisse(id),
    CONSTRAINT fk_operation_banque    FOREIGN KEY (banque_id)    REFERENCES banque(id),
    CONSTRAINT chk_operation_type     CHECK (type_operation IN ('ENTREE', 'SORTIE')),
    CONSTRAINT chk_operation_mode
        CHECK (mode_paiement IN ('ESPECES', 'CHEQUE', 'VIREMENT', 'CARTE_BANCAIRE',
                                 'WAVE', 'ORANGE_MONEY', 'FREE_MONEY'))
);

-- ========== AUDIT LOGS ==========
-- (consultations admin uniquement, voir AuditLog entité)
CREATE TABLE IF NOT EXISTS audit_logs (
    id                BIGSERIAL PRIMARY KEY,
    created_at        TIMESTAMP    NOT NULL,
    action            VARCHAR(40)  NOT NULL,
    user_id           BIGINT,
    user_login        VARCHAR(50),
    user_matricule    VARCHAR(30),
    user_nom_complet  VARCHAR(200),
    user_role         VARCHAR(20),
    entity_type       VARCHAR(60),
    entity_id         BIGINT,
    entity_label      VARCHAR(200),
    ip_address        VARCHAR(45),
    user_agent        VARCHAR(500),
    http_method       VARCHAR(10),
    http_path         VARCHAR(255),
    success           BOOLEAN      NOT NULL,
    error_message     VARCHAR(500),
    details           VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_action     ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_user_id    ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity     ON audit_logs (entity_type, entity_id);

-- ========== INDEX (opérations / journaux / caisses) ==========
CREATE INDEX IF NOT EXISTS idx_operation_date    ON operations_caisse (date_operation);
CREATE INDEX IF NOT EXISTS idx_operation_caisse  ON operations_caisse (caisse_id);
CREATE INDEX IF NOT EXISTS idx_operation_journal ON operations_caisse (journal_id);
CREATE INDEX IF NOT EXISTS idx_operation_banque  ON operations_caisse (banque_id);
CREATE INDEX IF NOT EXISTS idx_journal_date      ON journaux_caisse   (date_journal);
CREATE INDEX IF NOT EXISTS idx_caisse_statut     ON caisses           (statut);

-- V2 : referentiel synthetique (assures, ayants droit, etablissements, medecins, pharmacies).
-- Toutes les donnees sont fictives. Pas d'index ici : ils vont dans un script separe.

CREATE TABLE centre_sante (
    id      UUID PRIMARY KEY,
    code    VARCHAR(20)  NOT NULL UNIQUE,
    nom     VARCHAR(200) NOT NULL,
    type    VARCHAR(20)  NOT NULL,
    ville   VARCHAR(100) NOT NULL,
    CONSTRAINT ck_centre_sante_type CHECK (type IN ('HOPITAL', 'CLINIQUE', 'CENTRE_SANTE', 'CMR'))
);

CREATE TABLE medecin (
    id               UUID PRIMARY KEY,
    code             VARCHAR(20)  NOT NULL UNIQUE,
    nom              VARCHAR(100) NOT NULL,
    prenoms          VARCHAR(150) NOT NULL,
    specialite       VARCHAR(100) NOT NULL,
    centre_sante_id  UUID         NOT NULL REFERENCES centre_sante (id)
);

CREATE TABLE pharmacie (
    id      UUID PRIMARY KEY,
    code    VARCHAR(20)  NOT NULL UNIQUE,
    nom     VARCHAR(200) NOT NULL,
    ville   VARCHAR(100) NOT NULL
);

-- numero_assure : 8 chiffres + cle de controle sur 2 chiffres = 97 - (8 chiffres modulo 97).
CREATE TABLE assure (
    id                 UUID PRIMARY KEY,
    numero_assure      CHAR(10)     NOT NULL UNIQUE,
    nom                VARCHAR(100) NOT NULL,
    prenoms            VARCHAR(150) NOT NULL,
    date_naissance     DATE         NOT NULL,
    genre              CHAR(1)      NOT NULL,
    statut_cotisation  VARCHAR(10)  NOT NULL,
    date_debut_droits  DATE         NOT NULL,
    date_fin_droits    DATE,
    CONSTRAINT ck_assure_numero CHECK (numero_assure ~ '^[0-9]{10}$'
        AND CAST(substr(numero_assure, 9, 2) AS INT) = 97 - (CAST(substr(numero_assure, 1, 8) AS BIGINT) % 97)),
    CONSTRAINT ck_assure_genre CHECK (genre IN ('M', 'F')),
    CONSTRAINT ck_assure_statut_cotisation CHECK (statut_cotisation IN ('A_JOUR', 'EN_RETARD')),
    CONSTRAINT ck_assure_droits CHECK (date_fin_droits IS NULL OR date_fin_droits >= date_debut_droits)
);

CREATE TABLE ayant_droit (
    id              UUID PRIMARY KEY,
    assure_id       UUID         NOT NULL REFERENCES assure (id) ON DELETE CASCADE,
    nom             VARCHAR(100) NOT NULL,
    prenoms         VARCHAR(150) NOT NULL,
    date_naissance  DATE         NOT NULL,
    genre           CHAR(1)      NOT NULL,
    lien_parente    VARCHAR(10)  NOT NULL,
    CONSTRAINT ck_ayant_droit_genre CHECK (genre IN ('M', 'F')),
    CONSTRAINT ck_ayant_droit_lien CHECK (lien_parente IN ('CONJOINT', 'ENFANT'))
);


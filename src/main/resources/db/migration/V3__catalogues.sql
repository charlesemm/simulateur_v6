-- V3 : catalogues (actes, examens, medicaments). Tarifs fictifs en NUMERIC(14,2), en FCFA.
-- Les taux de prise en charge sont configurables et arrivent avec la tarification.

CREATE TABLE acte (
    id         UUID PRIMARY KEY,
    code       VARCHAR(20)   NOT NULL UNIQUE,
    libelle    VARCHAR(200)  NOT NULL,
    categorie  VARCHAR(20)   NOT NULL,
    tarif      NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_acte_categorie CHECK (categorie IN ('CONSULTATION', 'HOSPITALISATION', 'DENTAIRE')),
    CONSTRAINT ck_acte_tarif CHECK (tarif >= 0)
);

CREATE TABLE examen (
    id       UUID PRIMARY KEY,
    code     VARCHAR(20)   NOT NULL UNIQUE,
    libelle  VARCHAR(200)  NOT NULL,
    type     VARCHAR(20)   NOT NULL,
    tarif    NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_examen_type CHECK (type IN ('BIOLOGIE', 'IMAGERIE')),
    CONSTRAINT ck_examen_tarif CHECK (tarif >= 0)
);

CREATE TABLE medicament (
    id       UUID PRIMARY KEY,
    code     VARCHAR(20)   NOT NULL UNIQUE,
    libelle  VARCHAR(200)  NOT NULL,
    tarif    NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_medicament_tarif CHECK (tarif >= 0)
);

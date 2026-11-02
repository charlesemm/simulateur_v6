-- V4 : facture (feuille de soins), lignes et historique des transitions.
-- Dates d'ouverture/finalisation en temps SIMULE ; horodatage reel de l'historique = now().
-- run_id sans cle etrangere pour l'instant (la contrainte vers simulation_run arrive a la brique 6).
-- Pas d'index ici : ils vont dans un script separe.

CREATE SEQUENCE seq_numero_facture START WITH 1 INCREMENT BY 1;

CREATE TABLE facture (
    id                             UUID PRIMARY KEY,
    run_id                         UUID          NOT NULL,
    numero_facture                 VARCHAR(20)   NOT NULL UNIQUE,
    assure_id                      UUID          NOT NULL REFERENCES assure (id),
    beneficiaire_id                UUID          NOT NULL, -- assure ou ayant droit : pas de cle etrangere
    centre_sante_id                UUID          NOT NULL REFERENCES centre_sante (id),
    type_bon                       VARCHAR(20)   NOT NULL,
    statut                         VARCHAR(25)   NOT NULL,
    date_ouverture                 TIMESTAMP     NOT NULL,
    date_finalisation              TIMESTAMP,
    montant_total                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    part_cnam                      NUMERIC(14,2) NOT NULL DEFAULT 0,
    part_assurance_complementaire  NUMERIC(14,2) NOT NULL DEFAULT 0,
    part_assure                    NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_facture_numero CHECK (numero_facture ~ '^FAC-[0-9]{4}-[0-9]{6,}$'),
    CONSTRAINT ck_facture_type_bon CHECK (type_bon IN ('AMBULATOIRE', 'HOSPITALISATION', 'BIOLOGIE_IMAGERIE', 'DENTAIRE')),
    CONSTRAINT ck_facture_statut CHECK (statut IN ('OUVERTE', 'EN_COURS', 'PRESCRITE', 'MEDICAMENTS_DELIVRES',
        'FINALISEE', 'REFUSEE', 'ABANDONNEE')),
    CONSTRAINT ck_facture_montants CHECK (montant_total >= 0 AND part_cnam >= 0
        AND part_assurance_complementaire >= 0 AND part_assure >= 0
        AND part_cnam + part_assurance_complementaire + part_assure = montant_total),
    CONSTRAINT ck_facture_finalisation CHECK (date_finalisation IS NULL OR statut = 'FINALISEE')
);

CREATE TABLE facture_ligne (
    id                             UUID PRIMARY KEY,
    run_id                         UUID          NOT NULL,
    facture_id                     UUID          NOT NULL REFERENCES facture (id) ON DELETE CASCADE,
    numero_ligne                   INT           NOT NULL,
    code                           VARCHAR(30)   NOT NULL,
    designation                    VARCHAR(200)  NOT NULL,
    date_soin                      TIMESTAMP     NOT NULL, -- temps simule
    quantite                       INT           NOT NULL,
    prix_unitaire                  NUMERIC(14,2) NOT NULL,
    montant                        NUMERIC(14,2) NOT NULL,
    part_cnam                      NUMERIC(14,2) NOT NULL DEFAULT 0,
    part_assurance_complementaire  NUMERIC(14,2) NOT NULL DEFAULT 0,
    part_assure                    NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_facture_ligne_numero UNIQUE (facture_id, numero_ligne),
    CONSTRAINT ck_facture_ligne_quantite CHECK (quantite > 0),
    CONSTRAINT ck_facture_ligne_montants CHECK (prix_unitaire >= 0 AND montant >= 0 AND part_cnam >= 0
        AND part_assurance_complementaire >= 0 AND part_assure >= 0
        AND part_cnam + part_assurance_complementaire + part_assure = montant)
);

CREATE TABLE facture_historique (
    id                UUID PRIMARY KEY,
    run_id            UUID         NOT NULL,
    facture_id        UUID         NOT NULL REFERENCES facture (id) ON DELETE CASCADE,
    ancien_statut     VARCHAR(25), -- NULL a la creation de la facture
    nouveau_statut    VARCHAR(25)  NOT NULL,
    horodatage_simule TIMESTAMP    NOT NULL,
    horodatage_reel   TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_facture_historique_ancien CHECK (ancien_statut IS NULL OR ancien_statut IN ('OUVERTE', 'EN_COURS',
        'PRESCRITE', 'MEDICAMENTS_DELIVRES', 'FINALISEE', 'REFUSEE', 'ABANDONNEE')),
    CONSTRAINT ck_facture_historique_nouveau CHECK (nouveau_statut IN ('OUVERTE', 'EN_COURS', 'PRESCRITE',
        'MEDICAMENTS_DELIVRES', 'FINALISEE', 'REFUSEE', 'ABANDONNEE'))
);

-- V6 : scenarios et runs de simulation, puis cle etrangere facture.run_id -> simulation_run.
-- Pas d'index ici : ils vont dans un script separe.

CREATE TABLE scenario (
    id             UUID PRIMARY KEY,
    nom            VARCHAR(200) NOT NULL UNIQUE,
    contenu_yaml   TEXT         NOT NULL,
    date_creation  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    date_maj       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE simulation_run (
    id              UUID PRIMARY KEY,
    scenario_id     UUID         NOT NULL REFERENCES scenario (id),
    seed            BIGINT       NOT NULL,
    statut          VARCHAR(10)  NOT NULL,
    debut_simule    TIMESTAMP    NOT NULL,           -- origine du temps simule
    date_creation   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    date_debut      TIMESTAMPTZ,                     -- temps reel
    date_fin        TIMESTAMPTZ,                     -- temps reel
    parametres      JSONB        NOT NULL,           -- acceleration, debits, parcours, phases
    message_erreur  TEXT,
    CONSTRAINT ck_simulation_run_statut CHECK (statut IN ('CREE', 'EN_COURS', 'EN_PAUSE', 'ARRETE', 'ECHOUE')),
    CONSTRAINT ck_simulation_run_dates CHECK (date_fin IS NULL OR date_debut IS NULL OR date_fin >= date_debut)
);

-- Supprimer un run supprime ses factures (puis leurs lignes et historiques, deja en cascade).
ALTER TABLE facture
    ADD CONSTRAINT fk_facture_run FOREIGN KEY (run_id) REFERENCES simulation_run (id) ON DELETE CASCADE;

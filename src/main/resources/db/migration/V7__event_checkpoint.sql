-- V7 : evenements ecrits par lots et point de reprise par run.
-- Pas d'index ici (hors cles primaires) : ils vont dans un script separe.

CREATE TABLE evenement (
    event_id          UUID         PRIMARY KEY,            -- rejouer un lot ne cree pas de doublon (ON CONFLICT)
    run_id            UUID         NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    facture_id        UUID,                                -- nul pour DROITS_VERIFIES (facture pas encore ouverte)
    domaine           VARCHAR(50)  NOT NULL,
    type              VARCHAR(80)  NOT NULL,
    timestamp_simule  TIMESTAMP    NOT NULL,               -- temps simule (date fictive du soin)
    timestamp_reel    TIMESTAMPTZ  NOT NULL,               -- temps reel (ecriture du lot)
    payload           JSONB        NOT NULL,
    statut            VARCHAR(20)  NOT NULL DEFAULT 'ECRIT'
);

CREATE TABLE checkpoint_evenement (
    run_id            UUID         PRIMARY KEY REFERENCES simulation_run (id) ON DELETE CASCADE,
    dernier_event_id  UUID         NOT NULL,               -- dernier evenement du dernier lot valide
    compteur          BIGINT       NOT NULL,               -- evenements reellement inseres pour ce run
    date_maj          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

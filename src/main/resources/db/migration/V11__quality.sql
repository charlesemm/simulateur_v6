-- V11 : detections de l'outil de qualite, comparees ensuite a la verite terrain (anomaly_injected).
-- Pas d'index ici (hors contraintes) : ils vont dans un script separe.
-- Un rapport = un envoi de l'outil qualite pour un run. Plusieurs envois par run sont acceptes.

CREATE TABLE quality_report (
    id            UUID         PRIMARY KEY,
    run_id        UUID         NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    version_outil VARCHAR(80)  NOT NULL,                    -- version de l'outil qualite, texte libre
    date_reception TIMESTAMPTZ NOT NULL DEFAULT now()       -- temps reel
);

CREATE TABLE quality_detection (
    id         BIGINT           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id  UUID             NOT NULL REFERENCES quality_report (id) ON DELETE CASCADE,
    run_id     UUID             NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    event_id   UUID             NOT NULL REFERENCES evenement (event_id) ON DELETE CASCADE,
    type       VARCHAR(40)      NOT NULL,
    champ      VARCHAR(80),                                 -- nul pour un doublon (evenement entier)
    regle      VARCHAR(200),                                -- regle declenchee cote outil, facultatif
    score      DOUBLE PRECISION,                            -- confiance de l'outil, facultatif (pas un montant)
    CONSTRAINT ck_quality_detection_type CHECK (type IN (
        'CHAMP_VIDE', 'FAUTE_ORTHOGRAPHE', 'ESPACES_PARASITES', 'CASSE_INCOHERENTE', 'CARACTERES_SPECIAUX',
        'FORMAT_DATE_INVALIDE', 'FORMAT_NUMERIQUE_INVALIDE', 'FORMAT_IDENTIFIANT_INVALIDE', 'VALEUR_NEGATIVE',
        'VALEUR_HORS_LISTE', 'DOUBLON_EXACT')),
    -- Renvoyer la meme detection ne cree pas de seconde ligne (NULLS NOT DISTINCT : le champ nul d'un doublon compte).
    CONSTRAINT uq_quality_detection UNIQUE NULLS NOT DISTINCT (report_id, event_id, type, champ)
);

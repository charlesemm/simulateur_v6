-- V8 : verite terrain des anomalies injectees. Pas d'index ici (hors contraintes) : ils vont dans un script separe.
-- Une ligne = une anomalie sur un evenement ecrit. Les tables metier restent propres : seul l'evenement porte la
-- version corrompue, et cette table garde la valeur d'origine.

CREATE TABLE anomaly_injected (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- ordre d'ecriture
    run_id            UUID         NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    event_id          UUID         NOT NULL REFERENCES evenement (event_id) ON DELETE CASCADE,
    facture_id        UUID,                                                    -- nul si l'evenement n'a pas de facture
    type              VARCHAR(40)  NOT NULL,
    champ             VARCHAR(80),                                             -- nul pour un doublon (evenement entier)
    valeur_origine    TEXT,                                                    -- valeur propre, en texte
    valeur_corrompue  TEXT,                                                    -- nul si le champ a ete mis a null
    doublon_de        UUID         REFERENCES evenement (event_id) ON DELETE CASCADE,
    CONSTRAINT ck_anomaly_injected_type CHECK (type IN (
        'CHAMP_VIDE', 'FAUTE_ORTHOGRAPHE', 'ESPACES_PARASITES', 'CASSE_INCOHERENTE', 'CARACTERES_SPECIAUX',
        'FORMAT_DATE_INVALIDE', 'FORMAT_NUMERIQUE_INVALIDE', 'FORMAT_IDENTIFIANT_INVALIDE', 'VALEUR_NEGATIVE',
        'VALEUR_HORS_LISTE', 'DOUBLON_EXACT')),
    -- Un doublon reference son original, et lui seul.
    CONSTRAINT ck_anomaly_injected_doublon CHECK ((type = 'DOUBLON_EXACT') = (doublon_de IS NOT NULL)),
    -- Rejouer un lot ne cree pas de seconde ligne (NULLS NOT DISTINCT : le champ nul d'un doublon compte).
    CONSTRAINT uq_anomaly_injected UNIQUE NULLS NOT DISTINCT (event_id, type, champ)
);

-- V9 : actions de chaos (crash tests) declenchees pendant un run. Pas d'index ici : ils vont dans un script separe.
-- Une ligne = une action : qui l'a lancee, quand, pour combien de temps, et quand elle a vraiment pris fin.

CREATE TABLE chaos_action (
    id                  UUID         PRIMARY KEY,
    run_id              UUID         NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    type                VARCHAR(30)  NOT NULL,
    parametres          JSONB        NOT NULL,                 -- ex. {"mode":"DESACTIVER"} ou {"latenceMs":500}
    debut               TIMESTAMPTZ  NOT NULL,                 -- temps reel
    fin_prevue          TIMESTAMPTZ  NOT NULL,                 -- debut + duree demandee (duree maximale imposee)
    fin                 TIMESTAMPTZ,                           -- nul tant que l'action est active
    motif_fin           VARCHAR(20),                           -- EXPIREE, RETABLIE, RUN_TERMINE, INTERROMPUE
    auteur              VARCHAR(100) NOT NULL,
    evenements_touches  BIGINT       NOT NULL DEFAULT 0,       -- MESSAGE_TRONQUE : evenements tronques
    CONSTRAINT ck_chaos_action_type CHECK (type IN ('COUPURE_BASE', 'LATENCE', 'SURCHARGE', 'MESSAGE_TRONQUE')),
    CONSTRAINT ck_chaos_action_motif CHECK (motif_fin IS NULL
        OR motif_fin IN ('EXPIREE', 'RETABLIE', 'RUN_TERMINE', 'INTERROMPUE')),
    CONSTRAINT ck_chaos_action_dates CHECK (fin_prevue > debut AND (fin IS NULL OR fin >= debut)),
    CONSTRAINT ck_chaos_action_fin CHECK ((fin IS NULL) = (motif_fin IS NULL))
);

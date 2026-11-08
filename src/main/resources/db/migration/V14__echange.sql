-- V14 : trace des echanges avec l'outil d'analyse. Une ligne par echange, horodatee et conservee
-- (cahier 4.5 : la campagne doit rester integralement tracable en cas de litige).
--
-- Echo n'appelle JAMAIS l'outil : c'est un script exterieur qui vient chercher le jeu (pull) puis
-- pousse son rapport. Un ENVOI est donc inscrit quand le script RETIRE l'export, une RECEPTION
-- quand il pousse un rapport, qu'il soit accepte ou refuse.
--
-- Les trois echecs d'echange ont chacun leur statut : aucun ne doit jamais etre confondu avec un
-- taux de detection de 0 %.
--   SILENCE              : l'export a ete retire, aucun rapport n'est arrive dans le delai regle.
--   RAPPORT_MALFORME     : l'envoi a ete refuse en bloc (422 ou 400).
--   RAPPORT_SANS_DETAIL  : rapport accepte, mais aucune detection exploitable (cahier 2.3 / 4.3).
--
-- SILENCE n'est pas ecrit ici : il se deduit a la lecture (export retire + delai depasse + aucun
-- rapport). La valeur est acceptee par la contrainte car la planification du prompt 15 l'inscrira.
-- Pas d'index : ils vont dans un script separe.

CREATE TABLE echange (
    id            UUID         PRIMARY KEY,
    run_id        UUID         NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    sens          VARCHAR(10)  NOT NULL,
    date_echange  TIMESTAMPTZ  NOT NULL DEFAULT now(),   -- temps reel
    version_outil VARCHAR(80),                           -- nul pour un retrait d'export
    statut        VARCHAR(30)  NOT NULL,
    detail        VARCHAR(500),
    CONSTRAINT ck_echange_sens CHECK (sens IN ('ENVOI', 'RECEPTION')),
    CONSTRAINT ck_echange_statut CHECK (statut IN (
        'EXPORT_RETIRE', 'RAPPORT_RECU', 'SILENCE', 'RAPPORT_MALFORME', 'RAPPORT_SANS_DETAIL'))
);

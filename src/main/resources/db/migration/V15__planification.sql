-- V15 : la planification des analyses. « Chaque jour a 3 h, analyse la derniere simulation terminee. »
--
-- Echo tient le CALENDRIER, il ne declenche RIEN : il ouvre une echeance, et le script exterieur
-- vient la lire par GET /api/analyses/attendues (prompt 14). Le script reste maitre de son
-- execution — il peut suivre le calendrier, ou travailler sans lui.
--
-- Deux tables :
--   planification_analyse : la regle (« a telle heure, tels jours, sur telle cible »).
--   analyse_attendue      : une ligne par ECHEANCE reellement ouverte, et son suivi.
--
-- La colonne cible n'a qu'une valeur aujourd'hui (DERNIERE_SIMULATION_TERMINEE). Elle existe quand
-- meme : une autre cible n'imposera pas une migration de plus.
--
-- Pas d'index ici : ils vont dans un script separe.

CREATE TABLE planification_analyse (
    id            UUID         PRIMARY KEY,
    libelle       VARCHAR(120) NOT NULL,
    heure         TIME         NOT NULL,                 -- heure du SERVEUR (fuseau fixe en configuration)
    jours_semaine VARCHAR(100) NOT NULL,                 -- 'TOUS' ou 'LUNDI,MARDI,...'
    cible         VARCHAR(40)  NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT true,
    date_creation TIMESTAMPTZ  NOT NULL DEFAULT now(),   -- temps reel
    date_maj      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_planification_cible CHECK (cible IN ('DERNIERE_SIMULATION_TERMINEE'))
);

-- Une echeance. planification_id est NULLABLE : une analyse peut etre demandee a la main, et une
-- planification supprimee ne doit pas effacer l'historique de ce qu'elle a declenche (SET NULL).
-- run_id est NULLABLE : au moment de l'ouverture, il n'y a peut-etre AUCUNE simulation terminee a
-- viser. L'echeance s'ouvre quand meme, au statut SANS_CIBLE, avec son explication : elle ne tombe
-- pas en erreur et ne disparait pas silencieusement.
--
-- L'unicite (planification_id, date_echeance) est ce qui rend l'ouverture IDEMPOTENTE : deux
-- passages de la tache dans la meme minute n'ouvrent pas deux echeances.
CREATE TABLE analyse_attendue (
    id               UUID        PRIMARY KEY,
    planification_id UUID        REFERENCES planification_analyse (id) ON DELETE SET NULL,
    run_id           UUID        REFERENCES simulation_run (id) ON DELETE CASCADE,
    date_echeance    TIMESTAMPTZ NOT NULL,
    date_retrait     TIMESTAMPTZ,
    date_rapport     TIMESTAMPTZ,
    statut           VARCHAR(20) NOT NULL,
    detail           VARCHAR(500),
    CONSTRAINT ck_analyse_attendue_statut CHECK (statut IN (
        'ATTENDUE', 'RETIREE', 'RAPPORT_RECU', 'SILENCE', 'RAPPORT_REFUSE', 'SANS_CIBLE')),
    CONSTRAINT uk_analyse_attendue_echeance UNIQUE (planification_id, date_echeance)
);

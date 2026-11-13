-- V22 : les contraintes manquantes sur les evenements, et run_id des lignes et de l'historique.
--
-- 1. CHECK sur statut, type et domaine de TB_ECHO_EVENEMENT. Les listes viennent du code Java :
--      statut  : core.event.StatutEcriture
--      type    : union des FlowGenerator.typesEvenements() (aujourd'hui PrestationEventType)
--      domaine : les FlowGenerator.domaine() (aujourd'hui "prestations")
--    IndexEtContraintesIntegrationTest verifie que le code et ces CHECK disent la meme chose :
--    un type ajoute plus tard sans migration fait echouer le build, pas un lot en production.
-- 2. Cle etrangere run_id sur TB_ECHO_FACTURE_LIGNE et TB_ECHO_FACTURE_HISTORIQUE (la cascade passait
--    deja par la facture, mais rien ne garantissait que la colonne designe un run existant).
--    Les index de ces colonnes sont poses par V21.
--
-- Base remplie : avant d'ajouter quoi que ce soit, le bloc ci-dessous compte les lignes qui
-- violeraient une contrainte. S'il y en a, la migration S'ARRETE, sans rien modifier ni rattacher en
-- silence, et dit lesquelles. Les lister : scripts/db/controle-avant-v22.sql. Quoi faire :
-- docs/05-deploiement.md, section 9 ter.

DO $$
DECLARE
    statuts   bigint;
    types     bigint;
    domaines  bigint;
    lignes    bigint;
    historique bigint;
BEGIN
    SELECT count(*) INTO statuts FROM "TB_ECHO_EVENEMENT"
     WHERE statut NOT IN ('ECRIT', 'ECHOUE');
    SELECT count(*) INTO types FROM "TB_ECHO_EVENEMENT"
     WHERE type NOT IN ('PATIENT_ARRIVE', 'DROITS_VERIFIES', 'ACCES_REFUSE', 'FACTURE_OUVERTE',
                        'CONSULTATION_REALISEE', 'EXAMEN_REALISE', 'HOSPITALISATION_REALISEE',
                        'PRESCRIPTION_EMISE', 'MEDICAMENTS_DELIVRES', 'FACTURE_FINALISEE');
    SELECT count(*) INTO domaines FROM "TB_ECHO_EVENEMENT"
     WHERE domaine NOT IN ('prestations');
    SELECT count(*) INTO lignes FROM "TB_ECHO_FACTURE_LIGNE" l
     WHERE NOT EXISTS (SELECT 1 FROM "TB_ECHO_SIMULATION_RUN" r WHERE r.id = l.run_id);
    SELECT count(*) INTO historique FROM "TB_ECHO_FACTURE_HISTORIQUE" h
     WHERE NOT EXISTS (SELECT 1 FROM "TB_ECHO_SIMULATION_RUN" r WHERE r.id = h.run_id);

    IF statuts + types + domaines + lignes + historique > 0 THEN
        RAISE EXCEPTION 'V22 refusee : lignes existantes hors contrainte (evenements : % statut, % type, '
                        '% domaine ; % ligne(s) de facture et % historique(s) sans run). Rien n''a ete '
                        'modifie. Les lister avec scripts/db/controle-avant-v22.sql, puis suivre '
                        'docs/05-deploiement.md (9 ter).', statuts, types, domaines, lignes, historique;
    END IF;
END
$$;

ALTER TABLE "TB_ECHO_EVENEMENT"
    ADD CONSTRAINT ck_evenement_statut CHECK (statut IN ('ECRIT', 'ECHOUE')),
    ADD CONSTRAINT ck_evenement_type CHECK (type IN (
        'PATIENT_ARRIVE', 'DROITS_VERIFIES', 'ACCES_REFUSE', 'FACTURE_OUVERTE', 'CONSULTATION_REALISEE',
        'EXAMEN_REALISE', 'HOSPITALISATION_REALISEE', 'PRESCRIPTION_EMISE', 'MEDICAMENTS_DELIVRES',
        'FACTURE_FINALISEE')),
    ADD CONSTRAINT ck_evenement_domaine CHECK (domaine IN ('prestations'));

ALTER TABLE "TB_ECHO_FACTURE_LIGNE"
    ADD CONSTRAINT fk_facture_ligne_run FOREIGN KEY (run_id)
        REFERENCES "TB_ECHO_SIMULATION_RUN" (id) ON DELETE CASCADE;

ALTER TABLE "TB_ECHO_FACTURE_HISTORIQUE"
    ADD CONSTRAINT fk_facture_historique_run FOREIGN KEY (run_id)
        REFERENCES "TB_ECHO_SIMULATION_RUN" (id) ON DELETE CASCADE;

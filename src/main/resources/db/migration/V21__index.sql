-- V21 : les index. Rien d'autre (les contraintes sont dans V22).
--
-- C'est le « script separe » annonce en tete des migrations precedentes. Avant lui, la base
-- n'avait aucun index hors cles primaires et unicites : 31 cles etrangeres sans index couvrant
-- a V20. Lire les evenements d'un run, ou supprimer un run, parcourait des tables entieres ;
-- la suppression d'un run de 100 000 evenements prenait 61 s (mesures : docs/journal.md).
--
-- Regle de CLAUDE.md (section 7) : run_id, statut, facture_id et les besoins mesures. Chaque
-- index hors de cette liste porte ci-dessous la requete qui le justifie.
--
-- Pourquoi indexer une cle etrangere : supprimer (ou changer la cle d') une ligne parente fait
-- executer par PostgreSQL, pour CHAQUE ligne supprimee, le controle
--     SELECT 1 FROM ONLY <enfant> x WHERE <colonne> = $1 FOR KEY SHARE OF x
-- (ou le DELETE de la cascade). Sans index sur <colonne>, c'est un parcours complet de l'enfant
-- par ligne parente. La requete du critere de fin (journal du prompt 20) doit ne renvoyer plus
-- aucune cle etrangere sans index.
--
-- Pas de CREATE INDEX CONCURRENTLY : Flyway joue le script dans une transaction, et la montee se
-- fait application arretee (procedure de docs/05-deploiement.md), donc aucune ecriture a bloquer.

-- ===================================================================================
-- 1. Tables d'un run : run_id (cascade depuis TB_ECHO_SIMULATION_RUN, lecture par run)
-- ===================================================================================

-- Evenements. L'explorateur et l'API filtrent par run, puis par facture ou par type :
--   EvenementRepository.rechercher : where run_id = ? [and type = ?] order by timestamp_reel, event_id
-- (run_id, facture_id) couvre aussi la cle etrangere evenement.run_id.
CREATE INDEX idx_evenement_run_facture ON "TB_ECHO_EVENEMENT" (run_id, facture_id);
CREATE INDEX idx_evenement_run_type    ON "TB_ECHO_EVENEMENT" (run_id, type);

-- MESURE : la page de l'explorateur trie sur (timestamp_reel, event_id). Sans cet index, aucun des
-- deux precedents ne sert le tri : parcours de tout le run, puis tri, qui deborde sur disque
-- (external merge, 17 Mo) des la page 1000 d'un run de 100 000 evenements :
--   select ... from "TB_ECHO_EVENEMENT" where run_id = ? order by timestamp_reel, event_id
--   limit 50 offset ?
CREATE INDEX idx_evenement_run_chrono  ON "TB_ECHO_EVENEMENT" (run_id, timestamp_reel, event_id);

CREATE INDEX idx_facture_run           ON "TB_ECHO_FACTURE" (run_id);
-- facture_ligne et facture_historique : la cle etrangere run_id arrive avec V22.
CREATE INDEX idx_facture_ligne_run     ON "TB_ECHO_FACTURE_LIGNE" (run_id);
CREATE INDEX idx_facture_historique_run ON "TB_ECHO_FACTURE_HISTORIQUE" (run_id);

-- Anomalies : la verite terrain se lit par run et par type.
--   QualiteRepository : select type, count(*) from "TB_ECHO_ANOMALY_INJECTED" where run_id = ? group by type
CREATE INDEX idx_anomaly_injected_run_type ON "TB_ECHO_ANOMALY_INJECTED" (run_id, type);

CREATE INDEX idx_chaos_action_run      ON "TB_ECHO_CHAOS_ACTION" (run_id);
CREATE INDEX idx_quality_report_run    ON "TB_ECHO_QUALITY_REPORT" (run_id);
CREATE INDEX idx_quality_detection_run ON "TB_ECHO_QUALITY_DETECTION" (run_id);
CREATE INDEX idx_export_fichier_run    ON "TB_ECHO_EXPORT_FICHIER" (run_id);
CREATE INDEX idx_echange_run           ON "TB_ECHO_ECHANGE" (run_id);
CREATE INDEX idx_analyse_attendue_run  ON "TB_ECHO_ANALYSE_ATTENDUE" (run_id);

-- ===================================================================================
-- 2. facture_id et statut
-- ===================================================================================

-- Historique : cascade depuis la facture (la cle etrangere facture_id n'avait pas d'index).
-- TB_ECHO_FACTURE_LIGNE (facture_id) est deja couvert par l'unicite (facture_id, numero_ligne).
CREATE INDEX idx_facture_historique_facture ON "TB_ECHO_FACTURE_HISTORIQUE" (facture_id);

-- Statut de la facture : demande par le prompt, et cle etrangere vers TB_TV_STATUTS_FACTURES.
CREATE INDEX idx_facture_statut ON "TB_ECHO_FACTURE" (statut);

-- ===================================================================================
-- 3. Cles etrangeres vers les evenements : MESURE, la cascade d'une suppression de run
-- ===================================================================================
-- Supprimer un run supprime ses evenements ; pour chacun, PostgreSQL cherche les anomalies qui le
-- designent comme doublon_de et les detections qui le designent :
--   DELETE FROM ONLY "TB_ECHO_ANOMALY_INJECTED"  WHERE doublon_de = $1
--   DELETE FROM ONLY "TB_ECHO_QUALITY_DETECTION" WHERE event_id   = $1
-- Sans ces deux index : un parcours complet de chaque table PAR evenement supprime.
-- (anomaly_injected.event_id est deja couvert par l'unicite (event_id, type, champ).)
CREATE INDEX idx_anomaly_injected_doublon_de ON "TB_ECHO_ANOMALY_INJECTED" (doublon_de);
CREATE INDEX idx_quality_detection_event     ON "TB_ECHO_QUALITY_DETECTION" (event_id);

-- ===================================================================================
-- 4. Cles etrangeres vers le referentiel synthetique : assure, centre, scenario
-- ===================================================================================

-- Demandes par le prompt : un assure et ses ayants droit, un assure et ses factures.
--   export (ExportRepository) : where exists (select 1 from "TB_ECHO_FACTURE" f
--                                             where f.assure_id = a.id and f.run_id = ?)
-- Les ayants droit sont lus une fois en memoire par le referentiel ; l'index sert le controle de
-- la cle etrangere ayant_droit.assure_id.
CREATE INDEX idx_ayant_droit_assure ON "TB_ECHO_AYANT_DROIT" (assure_id);
CREATE INDEX idx_facture_assure     ON "TB_ECHO_FACTURE" (assure_id);

-- Le referentiel et les scenarios ne sont jamais supprimes par l'application. Ces trois index ne
-- servent que le controle de la cle etrangere, si un centre ou un scenario est un jour retire :
--   SELECT 1 FROM ONLY "TB_ECHO_FACTURE"        x WHERE centre_sante_id = $1 FOR KEY SHARE OF x
--   SELECT 1 FROM ONLY "TB_ECHO_MEDECIN"        x WHERE centre_sante_id = $1 FOR KEY SHARE OF x
--   SELECT 1 FROM ONLY "TB_ECHO_SIMULATION_RUN" x WHERE scenario_id     = $1 FOR KEY SHARE OF x
CREATE INDEX idx_facture_centre_sante ON "TB_ECHO_FACTURE" (centre_sante_id);
CREATE INDEX idx_medecin_centre_sante ON "TB_ECHO_MEDECIN" (centre_sante_id);
CREATE INDEX idx_simulation_run_scenario ON "TB_ECHO_SIMULATION_RUN" (scenario_id);

-- ===================================================================================
-- 5. Cles etrangeres vers les tables de valeurs (V18, V19)
-- ===================================================================================
-- L'application ne supprime ni ne renomme jamais un code de table de valeurs. Ces index ne
-- servent qu'au controle de la cle etrangere quand une direction metier retire ou corrige un code :
--   SELECT 1 FROM ONLY <table> x WHERE <colonne> = $1 FOR KEY SHARE OF x
-- Ils sont poses pour que la requete du catalogue ne renvoie plus aucune cle sans index (critere
-- du prompt 20, decision du 2026-11-13). Leur cout a l'ecriture est chiffre dans le journal.

CREATE INDEX idx_facture_type_facture        ON "TB_ECHO_FACTURE" (type_facture_code);
CREATE INDEX idx_facture_historique_ancien   ON "TB_ECHO_FACTURE_HISTORIQUE" (ancien_statut);
CREATE INDEX idx_facture_historique_nouveau  ON "TB_ECHO_FACTURE_HISTORIQUE" (nouveau_statut);

CREATE INDEX idx_assure_genre                ON "TB_ECHO_ASSURE" (genre);
CREATE INDEX idx_assure_regime               ON "TB_ECHO_ASSURE" (regime_code);
CREATE INDEX idx_ayant_droit_genre           ON "TB_ECHO_AYANT_DROIT" (genre);
CREATE INDEX idx_centre_sante_departement    ON "TB_ECHO_CENTRE_SANTE" (departement_code);
CREATE INDEX idx_centre_sante_type_etab      ON "TB_ECHO_CENTRE_SANTE" (type_etablissement_code);
CREATE INDEX idx_pharmacie_departement       ON "TB_ECHO_PHARMACIE" (departement_code);
CREATE INDEX idx_medecin_specialite          ON "TB_ECHO_MEDECIN" (specialite_medicale_code);

CREATE INDEX idx_tv_regions_pays             ON "TB_TV_LOCALISATION_REGIONS" (pays_code);
CREATE INDEX idx_tv_departements_region      ON "TB_TV_LOCALISATION_DEPARTEMENTS" (region_code);
CREATE INDEX idx_tv_localites_departement    ON "TB_TV_LOCALISATION_LOCALITES" (departement_code);

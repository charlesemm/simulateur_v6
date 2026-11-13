-- Controle AVANT V22 (contraintes sur les evenements). Lecture seule : ce script ne modifie rien.
-- A jouer sur la base a V21 (ou a V20 : il ne lit que des colonnes qui existent depuis V17).
--
-- V22 refuse de s'appliquer tant qu'une ligne existante violerait une de ses contraintes. Ce script
-- liste ces lignes. Regle de la procedure (docs/05-deploiement.md, 9 ter) : s'il renvoie une ligne,
-- on ne migre pas ; on traite d'abord les lignes listees.
--
-- Les listes sont recopiees de V22 ; elles ne changent pas (un script applique ne se modifie
-- jamais). Teste par MonteeV22IntegrationTest et IndexEtContraintesIntegrationTest.

SELECT 'TB_ECHO_EVENEMENT' AS table_source, 'statut' AS colonne, statut AS valeur,
       count(*) AS lignes, count(DISTINCT run_id) AS runs
FROM "TB_ECHO_EVENEMENT"
WHERE statut NOT IN ('ECRIT', 'ECHOUE')
GROUP BY statut
UNION ALL
SELECT 'TB_ECHO_EVENEMENT', 'type', type, count(*), count(DISTINCT run_id)
FROM "TB_ECHO_EVENEMENT"
WHERE type NOT IN ('PATIENT_ARRIVE', 'DROITS_VERIFIES', 'ACCES_REFUSE', 'FACTURE_OUVERTE',
                   'CONSULTATION_REALISEE', 'EXAMEN_REALISE', 'HOSPITALISATION_REALISEE',
                   'PRESCRIPTION_EMISE', 'MEDICAMENTS_DELIVRES', 'FACTURE_FINALISEE')
GROUP BY type
UNION ALL
SELECT 'TB_ECHO_EVENEMENT', 'domaine', domaine, count(*), count(DISTINCT run_id)
FROM "TB_ECHO_EVENEMENT"
WHERE domaine NOT IN ('prestations')
GROUP BY domaine
UNION ALL
SELECT 'TB_ECHO_FACTURE_LIGNE', 'run_id', run_id::text, count(*), 1
FROM "TB_ECHO_FACTURE_LIGNE" l
WHERE NOT EXISTS (SELECT 1 FROM "TB_ECHO_SIMULATION_RUN" r WHERE r.id = l.run_id)
GROUP BY run_id
UNION ALL
SELECT 'TB_ECHO_FACTURE_HISTORIQUE', 'run_id', run_id::text, count(*), 1
FROM "TB_ECHO_FACTURE_HISTORIQUE" h
WHERE NOT EXISTS (SELECT 1 FROM "TB_ECHO_SIMULATION_RUN" r WHERE r.id = h.run_id)
GROUP BY run_id
ORDER BY 1, 2, 3;

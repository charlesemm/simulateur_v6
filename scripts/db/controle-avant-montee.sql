-- Controle AVANT la montee d'une base V12 vers V22. Lecture seule : ce script ne modifie rien.
--
-- V19 remplace la ville (centres, pharmacies) et la specialite (medecins) par une cle etrangere.
-- Une valeur qu'elle ne reconnait pas n'est PAS refusee : elle est rattachee en silence a un
-- defaut, Abidjan (D109) pour une ville, Medecine generale (MG) pour une specialite. La migration
-- passe donc toujours, mais une ville mal orthographiee se retrouverait a Abidjan sans que rien
-- ne le dise.
--
-- Regle de la procedure (docs/05-deploiement.md) : si ce script renvoie une ligne, on NE MIGRE PAS
-- la vraie base. On corrige d'abord la valeur sur la base a V12, puis on relance le controle.
--
-- Les listes ci-dessous sont recopiees de V18 (111 departements, specialites du catalogue) et de
-- V19 (les dix libelles convertis explicitement). Elles ne doivent changer que si V18 ou V19
-- changeaient, ce qui n'arrive pas : un script applique ne se modifie jamais.
-- Teste par MonteeV12IntegrationTest.

WITH departements (libelle) AS (SELECT unnest(ARRAY[
    'Agboville', 'Sikensi', 'Taabo', 'Tiassale', 'Koro', 'Ouaninou', 'Touba', 'Boundiali', 'Kouto',
    'Tengrela', 'Didievi', 'Djekanou', 'Tiebissou', 'Toumodi', 'Dianra', 'Kounahiri', 'Mankono',
    'Bouna', 'Doropo', 'Nassian', 'Tehini', 'Blolequin', 'Guiglo', 'Tai', 'Toulepleu', 'Kaniasso',
    'Minignan', 'Beoumi', 'Botro', 'Bouake', 'Sakassou', 'Fresco', 'Sassandra', 'Gagnoa', 'Oume',
    'Bondoukou', 'Koun-Fao', 'Sandegue', 'Tanda', 'Transua', 'Dabou', 'Grand-Lahou', 'Jacqueville',
    'Bangolo', 'Duekoue', 'Facobly', 'Kouibly', 'Dabakala', 'Katiola', 'Niakaramadougou', 'Daloa',
    'Issia', 'Vavoua', 'Zoukougbeu', 'Daoukro', 'M''Bahiakro', 'Ouelle', 'Prikro', 'Abengourou',
    'Agnibilekrou', 'Bettie', 'Gbeleban', 'Madinani', 'Odienne', 'Samatiguila', 'Seguelon', 'Adzope',
    'Akoupe', 'Alepe', 'Yakasse-Attobrou', 'Divo', 'Guitry', 'Lakota', 'Bonon', 'Bouafle', 'Gohitafla',
    'Sinfra', 'Zuenoula', 'Arrah', 'Bongouanou', 'M''Batto', 'Buyo', 'Gueyo', 'Meagui', 'Soubre',
    'Bocanda', 'Dimbokro', 'Kouassi-Kouassikro', 'Dikodougou', 'Korhogo', 'M''Bengue', 'Sinematiali',
    'San-Pedro', 'Tabou', 'Aboisso', 'Adiake', 'Grand-Bassam', 'Tiapoum', 'Ferkessedougou', 'Kong',
    'Ouangolodougou', 'Biankouma', 'Danane', 'Man', 'Sipilou', 'Zouan-Hounien', 'Kani', 'Seguela',
    'Abidjan', 'Attiegouakro', 'Yamoussoukro'
])), specialites (libelle) AS (SELECT unnest(ARRAY[
    -- convertis explicitement par V19
    'Medecine generale', 'Pediatrie', 'Gynecologie-obstetrique', 'Chirurgie generale', 'Cardiologie',
    'Chirurgie dentaire', 'Ophtalmologie', 'Radiologie', 'Biologie medicale', 'Medecine interne',
    -- reconnus par leur libelle exact dans le catalogue de V18
    'Medecine generale', 'Pediatrie', 'Gynecologie-obstetrique', 'Chirurgie generale',
    'Orthopedie-traumatologie', 'Maladies infectieuses', 'Pneumologie', 'Gastro-enterologie',
    'Nephrologie', 'Urologie', 'Neurologie', 'Psychiatrie', 'Dermatologie', 'Ophtalmologie',
    'Oto-rhino-laryngologie', 'Rhumatologie', 'Oncologie medicale', 'Odontostomatologie', 'Radiologie
    et imagerie medicale', 'Biologie medicale', 'Pharmacie', 'Sage-femme', 'Kinesitherapie',
    'Cardiologie', 'Diabetologie-endocrinologie', 'Anesthesie-reanimation'
]))
SELECT 'centre_sante' AS table_source, code, ville AS valeur, 'D109 (Abidjan)' AS rattachement_par_defaut
FROM centre_sante WHERE ville NOT IN (SELECT libelle FROM departements)
UNION ALL
SELECT 'pharmacie', code, ville, 'D109 (Abidjan)'
FROM pharmacie WHERE ville NOT IN (SELECT libelle FROM departements)
UNION ALL
SELECT 'medecin', code, specialite, 'MG (Medecine generale)'
FROM medecin WHERE specialite NOT IN (SELECT libelle FROM specialites)
-- V22 (contraintes sur les evenements) ne rattache rien : elle REFUSE de s'appliquer. Memes listes
-- que V22 et que scripts/db/controle-avant-v22.sql, sur les noms de tables de V12.
UNION ALL
SELECT 'evenement', 'statut', statut, 'REFUS DE V22 (' || count(*) || ' ligne(s))'
FROM evenement WHERE statut NOT IN ('ECRIT', 'ECHOUE') GROUP BY statut
UNION ALL
SELECT 'evenement', 'type', type, 'REFUS DE V22 (' || count(*) || ' ligne(s))'
FROM evenement WHERE type NOT IN ('PATIENT_ARRIVE', 'DROITS_VERIFIES', 'ACCES_REFUSE', 'FACTURE_OUVERTE',
                                  'CONSULTATION_REALISEE', 'EXAMEN_REALISE', 'HOSPITALISATION_REALISEE',
                                  'PRESCRIPTION_EMISE', 'MEDICAMENTS_DELIVRES', 'FACTURE_FINALISEE')
GROUP BY type
UNION ALL
SELECT 'evenement', 'domaine', domaine, 'REFUS DE V22 (' || count(*) || ' ligne(s))'
FROM evenement WHERE domaine NOT IN ('prestations') GROUP BY domaine
UNION ALL
SELECT 'facture_ligne', 'run_id', run_id::text, 'REFUS DE V22 (' || count(*) || ' ligne(s))'
FROM facture_ligne l WHERE NOT EXISTS (SELECT 1 FROM simulation_run r WHERE r.id = l.run_id) GROUP BY run_id
UNION ALL
SELECT 'facture_historique', 'run_id', run_id::text, 'REFUS DE V22 (' || count(*) || ' ligne(s))'
FROM facture_historique h WHERE NOT EXISTS (SELECT 1 FROM simulation_run r WHERE r.id = h.run_id) GROUP BY run_id
ORDER BY table_source, code;

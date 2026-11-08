-- V16 : les six nouveaux types d'anomalies (quatre de relation, deux de fichier).
-- V8 n'est PAS modifiee : elle est deja appliquee. On recree ici les deux contraintes qu'elle a posees,
-- en place, sur une table qui porte deja des lignes des 11 anciens types (ces lignes restent valides).
-- Pas d'index ici : ils vont dans le script d'index dedie.

-- 1. Le catalogue des types passe de 11 a 17.
ALTER TABLE anomaly_injected DROP CONSTRAINT ck_anomaly_injected_type;
ALTER TABLE anomaly_injected ADD CONSTRAINT ck_anomaly_injected_type CHECK (type IN (
    -- les 11 anomalies de champ (V8)
    'CHAMP_VIDE', 'FAUTE_ORTHOGRAPHE', 'ESPACES_PARASITES', 'CASSE_INCOHERENTE', 'CARACTERES_SPECIAUX',
    'FORMAT_DATE_INVALIDE', 'FORMAT_NUMERIQUE_INVALIDE', 'FORMAT_IDENTIFIANT_INVALIDE', 'VALEUR_NEGATIVE',
    'VALEUR_HORS_LISTE', 'DOUBLON_EXACT',
    -- les 4 anomalies de relation
    'DOUBLON_APPROXIMATIF', 'REFERENCE_BRISEE', 'INCOHERENCE_TEMPORELLE', 'INCOHERENCE_CROISEE',
    -- les 2 anomalies de fichier : posees a l'ecriture de l'export, la valeur d'origine est celle en base
    'CARACTERES_CASSES', 'FORMAT_DATE_FICHIER'));

-- 2. Le doublon approximatif renseigne doublon_de comme le doublon exact : la contrainte devient
--    « doublon_de non nul si et seulement si le type est un doublon, exact ou approximatif ».
ALTER TABLE anomaly_injected DROP CONSTRAINT ck_anomaly_injected_doublon;
ALTER TABLE anomaly_injected ADD CONSTRAINT ck_anomaly_injected_doublon
    CHECK ((type IN ('DOUBLON_EXACT', 'DOUBLON_APPROXIMATIF')) = (doublon_de IS NOT NULL));

-- uq_anomaly_injected UNIQUE NULLS NOT DISTINCT (event_id, type, champ) est INCHANGEE, et les six nouveaux
-- types la respectent : chacun n'ecrit qu'une ligne par evenement. Celui qui aurait pu la heurter est
-- INCOHERENCE_CROISEE, dont la contradiction porte sur deux champs (droitsOuverts et dateFinDroits) : elle ne
-- fausse donc qu'un seul des deux (la date de fin) et s'inscrit en une ligne. Deux lignes du meme type sur le
-- meme evenement, une par champ, seraient refusees.

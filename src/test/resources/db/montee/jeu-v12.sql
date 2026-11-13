-- Jeu d'essai d'une base a V12 qui porte deja des simulations. Donnees 100 % SYNTHETIQUES.
-- Ecrit avec les noms de tables de V12 (avant V17) : le code Java actuel ne sait plus ecrire a V12.
-- Charge par MonteeV12IntegrationTest, puis la base est migree jusqu'a la derniere version.
--
-- Ce que le jeu doit exercer :
--   . V16 : une anomalie de chacun des 11 types d'origine, dont un DOUBLON_EXACT avec doublon_de ;
--   . V17 : une ligne au moins dans chacune des 24 tables de V12 ;
--   . V19 : des villes variees (dont une mal orthographiee, une absente des tables de valeurs, une en
--     minuscules), des specialites converties, reconnues par libelle, et inconnues ;
--     les quatre types de bon et plusieurs statuts de facture.
--
-- Identifiants lisibles : le premier groupe de l'UUID donne la table.
--   11111111 run, 22222222 assure, 33333333 ayant droit, 44444444 centre, 55555555 medecin,
--   66666666 pharmacie, 77777777 catalogue, 88888888 facture, 99999999 ligne, aaaaaaaa historique,
--   bbbbbbbb evenement, cccccccc chaos, dddddddd securite, eeeeeeee qualite, f0f0f0f0 scenario.
-- info_application (1 ligne, V1) et taux (6 lignes, V5) sont deja remplis par les migrations.

-- Scenario et runs : un run termine (ARRETE), un run en echec.
INSERT INTO scenario (id, nom, contenu_yaml) VALUES
 ('f0f0f0f0-0000-0000-0000-000000000001', 'repetition-v12', 'nom: repetition-v12');

INSERT INTO simulation_run (id, scenario_id, seed, statut, debut_simule, date_debut, date_fin, parametres, message_erreur) VALUES
 ('11111111-0000-0000-0000-000000000001', 'f0f0f0f0-0000-0000-0000-000000000001', 42, 'ARRETE',
  '2026-01-05 08:00', '2026-11-01 10:00+00', '2026-11-01 10:05+00', '{"acceleration": 60}', NULL),
 ('11111111-0000-0000-0000-000000000002', 'f0f0f0f0-0000-0000-0000-000000000001', 43, 'ECHOUE',
  '2026-01-05 08:00', '2026-11-02 10:00+00', '2026-11-02 10:01+00', '{"acceleration": 60}', 'base injoignable');

-- Referentiel : assures (numero = 8 chiffres + cle 97 - n mod 97) et ayants droit.
INSERT INTO assure (id, numero_assure, nom, prenoms, date_naissance, genre, statut_cotisation, date_debut_droits, date_fin_droits) VALUES
 ('22222222-0000-0000-0000-000000000001', '1000000120', 'Fictif', 'Awa',     '1980-03-12', 'F', 'A_JOUR',    '2020-01-01', NULL),
 ('22222222-0000-0000-0000-000000000002', '1000000219', 'Factice', 'Kouame', '1975-07-30', 'M', 'EN_RETARD', '2020-01-01', '2025-12-31'),
 ('22222222-0000-0000-0000-000000000003', '1000000318', 'Essai', 'Yao',      '1992-11-02', 'M', 'A_JOUR',    '2021-06-01', NULL);

INSERT INTO ayant_droit (id, assure_id, nom, prenoms, date_naissance, genre, lien_parente) VALUES
 ('33333333-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000002', 'Factice', 'Adjoua', '1978-02-14', 'F', 'CONJOINT'),
 ('33333333-0000-0000-0000-000000000002', '22222222-0000-0000-0000-000000000001', 'Fictif', 'Junior',  '2015-09-01', 'M', 'ENFANT');

-- Centres : les quatre types, et des villes de toutes sortes.
INSERT INTO centre_sante (id, code, nom, type, ville) VALUES
 ('44444444-0000-0000-0000-000000000001', 'CS-T01', 'Hopital fictif 1',   'HOPITAL',      'Abidjan'),
 ('44444444-0000-0000-0000-000000000002', 'CS-T02', 'Clinique fictive 2', 'CLINIQUE',     'Bouake'),
 ('44444444-0000-0000-0000-000000000003', 'CS-T03', 'Centre fictif 3',    'CENTRE_SANTE', 'Yamousoukro'),  -- mal orthographiee
 ('44444444-0000-0000-0000-000000000004', 'CS-T04', 'CMR fictif 4',       'CMR',          'Tombouctou'),   -- absente des tables de valeurs
 ('44444444-0000-0000-0000-000000000005', 'CS-T05', 'Centre fictif 5',    'CENTRE_SANTE', 'Korhogo');

INSERT INTO pharmacie (id, code, nom, ville) VALUES
 ('66666666-0000-0000-0000-000000000001', 'PH-T01', 'Pharmacie fictive 1', 'Daloa'),
 ('66666666-0000-0000-0000-000000000002', 'PH-T02', 'Pharmacie fictive 2', 'korhogo'),  -- casse differente
 ('66666666-0000-0000-0000-000000000003', 'PH-T03', 'Pharmacie fictive 3', 'Man');

-- Medecins : conversion explicite de V19 (3), libelle exact du catalogue (1), specialite inconnue (1).
INSERT INTO medecin (id, code, nom, prenoms, specialite, centre_sante_id) VALUES
 ('55555555-0000-0000-0000-000000000001', 'MD-T01', 'Docteur', 'Un',     'Medecine generale',  '44444444-0000-0000-0000-000000000001'),
 ('55555555-0000-0000-0000-000000000002', 'MD-T02', 'Docteur', 'Deux',   'Radiologie',         '44444444-0000-0000-0000-000000000002'),
 ('55555555-0000-0000-0000-000000000003', 'MD-T03', 'Docteur', 'Trois',  'Cardiologie',        '44444444-0000-0000-0000-000000000003'),
 ('55555555-0000-0000-0000-000000000004', 'MD-T04', 'Docteur', 'Quatre', 'Dermatologie',       '44444444-0000-0000-0000-000000000004'),
 ('55555555-0000-0000-0000-000000000005', 'MD-T05', 'Docteur', 'Cinq',   'Specialite inconnue','44444444-0000-0000-0000-000000000005');

-- Catalogues.
INSERT INTO acte (id, code, libelle, categorie, tarif) VALUES
 ('77777777-0000-0000-0000-000000000001', 'ACT-T01', 'Consultation fictive', 'CONSULTATION', 5000.00);
INSERT INTO examen (id, code, libelle, type, tarif) VALUES
 ('77777777-0000-0000-0000-000000000002', 'EXA-T01', 'Numeration fictive', 'BIOLOGIE', 4000.00);
INSERT INTO medicament (id, code, libelle, tarif) VALUES
 ('77777777-0000-0000-0000-000000000003', 'MED-T01', 'Comprime fictif', 1500.00);

-- Factures : les quatre types de bon, cinq statuts, deux runs.
INSERT INTO facture (id, run_id, numero_facture, assure_id, beneficiaire_id, centre_sante_id, type_bon, statut,
                     date_ouverture, date_finalisation, montant_total, part_cnam, part_assurance_complementaire, part_assure) VALUES
 ('88888888-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'FAC-2026-000001',
  '22222222-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000001', '44444444-0000-0000-0000-000000000001',
  'AMBULATOIRE', 'FINALISEE', '2026-01-05 08:10', '2026-01-05 10:00', 10000.00, 7000.00, 1500.00, 1500.00),
 ('88888888-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', 'FAC-2026-000002',
  '22222222-0000-0000-0000-000000000002', '33333333-0000-0000-0000-000000000001', '44444444-0000-0000-0000-000000000002',
  'HOSPITALISATION', 'REFUSEE', '2026-01-05 08:20', NULL, 0, 0, 0, 0),
 ('88888888-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000001', 'FAC-2026-000003',
  '22222222-0000-0000-0000-000000000003', '22222222-0000-0000-0000-000000000003', '44444444-0000-0000-0000-000000000003',
  'BIOLOGIE_IMAGERIE', 'EN_COURS', '2026-01-05 08:30', NULL, 0, 0, 0, 0),
 ('88888888-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000001', 'FAC-2026-000004',
  '22222222-0000-0000-0000-000000000001', '33333333-0000-0000-0000-000000000002', '44444444-0000-0000-0000-000000000004',
  'DENTAIRE', 'ABANDONNEE', '2026-01-05 08:40', NULL, 3000.00, 1500.00, 750.00, 750.00),
 ('88888888-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000002', 'FAC-2026-000005',
  '22222222-0000-0000-0000-000000000002', '22222222-0000-0000-0000-000000000002', '44444444-0000-0000-0000-000000000005',
  'AMBULATOIRE', 'OUVERTE', '2026-01-05 08:50', NULL, 0, 0, 0, 0);

INSERT INTO facture_ligne (id, run_id, facture_id, numero_ligne, code, designation, date_soin, quantite, prix_unitaire,
                           montant, part_cnam, part_assurance_complementaire, part_assure) VALUES
 ('99999999-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001',
  1, 'ACT-T01', 'Consultation fictive', '2026-01-05 08:15', 1, 6000.00, 6000.00, 4200.00, 900.00, 900.00),
 ('99999999-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001',
  2, 'MED-T01', 'Comprime fictif', '2026-01-05 09:30', 2, 2000.00, 4000.00, 2800.00, 600.00, 600.00),
 ('99999999-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000004',
  1, 'ACT-T01', 'Soin dentaire fictif', '2026-01-05 08:45', 1, 3000.00, 3000.00, 1500.00, 750.00, 750.00);

INSERT INTO facture_historique (id, run_id, facture_id, ancien_statut, nouveau_statut, horodatage_simule) VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', NULL,                   'OUVERTE',              '2026-01-05 08:10'),
 ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'OUVERTE',              'EN_COURS',             '2026-01-05 08:15'),
 ('aaaaaaaa-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'EN_COURS',             'PRESCRITE',            '2026-01-05 09:00'),
 ('aaaaaaaa-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'PRESCRITE',            'MEDICAMENTS_DELIVRES', '2026-01-05 09:30'),
 ('aaaaaaaa-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'MEDICAMENTS_DELIVRES', 'FINALISEE',            '2026-01-05 10:00'),
 ('aaaaaaaa-0000-0000-0000-000000000006', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000002', NULL,                   'REFUSEE',              '2026-01-05 08:20'),
 ('aaaaaaaa-0000-0000-0000-000000000007', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000003', NULL,                   'OUVERTE',              '2026-01-05 08:30'),
 ('aaaaaaaa-0000-0000-0000-000000000008', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000003', 'OUVERTE',              'EN_COURS',             '2026-01-05 08:35'),
 ('aaaaaaaa-0000-0000-0000-000000000009', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000004', NULL,                   'OUVERTE',              '2026-01-05 08:40'),
 ('aaaaaaaa-0000-0000-0000-000000000010', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000004', 'OUVERTE',              'ABANDONNEE',           '2026-01-05 08:45'),
 ('aaaaaaaa-0000-0000-0000-000000000011', '11111111-0000-0000-0000-000000000002', '88888888-0000-0000-0000-000000000005', NULL,                   'OUVERTE',              '2026-01-05 08:50');

-- Evenements. bbbbbbbb-...-012 est le doublon exact de bbbbbbbb-...-004.
INSERT INTO evenement (event_id, run_id, facture_id, domaine, type, timestamp_simule, timestamp_reel, payload) VALUES
 ('bbbbbbbb-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', NULL,                                   'prestations', 'PATIENT_ARRIVE',        '2026-01-05 08:05', '2026-11-01 10:00:01+00', '{"assureId": "22222222-0000-0000-0000-000000000001"}'),
 ('bbbbbbbb-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', NULL,                                   'prestations', 'DROITS_VERIFIES',       '2026-01-05 08:06', '2026-11-01 10:00:01+00', '{"statutCotisation": "PEUT_ETRE"}'),
 ('bbbbbbbb-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'prestations', 'FACTURE_OUVERTE',       '2026-01-05 08:10', '2026-11-01 10:00:02+00', '{"numeroAssure": null}'),
 ('bbbbbbbb-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'prestations', 'CONSULTATION_REALISEE', '2026-01-05 08:15', '2026-11-01 10:00:02+00', '{"nomMedecin": "Docteu"}'),
 ('bbbbbbbb-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'prestations', 'PRESCRIPTION_EMISE',    '2026-01-05 09:00', '2026-11-01 10:00:03+00', '{"medicament": "  Comprime fictif "}'),
 ('bbbbbbbb-0000-0000-0000-000000000006', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'prestations', 'MEDICAMENTS_DELIVRES',  '2026-01-05 09:30', '2026-11-01 10:00:03+00', '{"pharmacie": "pHARMACIE FICTIVE 1"}'),
 ('bbbbbbbb-0000-0000-0000-000000000007', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'prestations', 'FACTURE_FINALISEE',     '2026-01-05 10:00', '2026-11-01 10:00:04+00', '{"nomAssure": "F#ct@f", "montantTotal": -10000.00}'),
 ('bbbbbbbb-0000-0000-0000-000000000008', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000002', 'prestations', 'ACCES_REFUSE',          '2026-01-05 08:20', '2026-11-01 10:00:04+00', '{"motif": "COTISATIONS_EN_RETARD"}'),
 ('bbbbbbbb-0000-0000-0000-000000000009', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000003', 'prestations', 'FACTURE_OUVERTE',       '2026-01-05 08:30', '2026-11-01 10:00:05+00', '{"dateOuverture": "01/05/2026 8h30"}'),
 ('bbbbbbbb-0000-0000-0000-000000000010', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000003', 'prestations', 'EXAMEN_REALISE',        '2026-01-05 08:35', '2026-11-01 10:00:05+00', '{"montant": "4 000,00 F"}'),
 ('bbbbbbbb-0000-0000-0000-000000000011', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000004', 'prestations', 'FACTURE_OUVERTE',       '2026-01-05 08:40', '2026-11-01 10:00:06+00', '{"numeroFacture": "FAC-26-4"}'),
 ('bbbbbbbb-0000-0000-0000-000000000012', '11111111-0000-0000-0000-000000000001', '88888888-0000-0000-0000-000000000001', 'prestations', 'CONSULTATION_REALISEE', '2026-01-05 08:15', '2026-11-01 10:00:06+00', '{"nomMedecin": "Docteu"}'),
 ('bbbbbbbb-0000-0000-0000-000000000013', '11111111-0000-0000-0000-000000000002', '88888888-0000-0000-0000-000000000005', 'prestations', 'FACTURE_OUVERTE',       '2026-01-05 08:50', '2026-11-02 10:00:01+00', '{}');

INSERT INTO checkpoint_evenement (run_id, dernier_event_id, compteur) VALUES
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000012', 12),
 ('11111111-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000013', 1);

-- Verite terrain : les 11 types d'origine, une ligne chacun, et un second CHAMP_VIDE.
INSERT INTO anomaly_injected (run_id, event_id, facture_id, type, champ, valeur_origine, valeur_corrompue, doublon_de) VALUES
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000003', '88888888-0000-0000-0000-000000000001', 'CHAMP_VIDE',                  'numeroAssure',     '1000000120',        NULL,                  NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000004', '88888888-0000-0000-0000-000000000001', 'FAUTE_ORTHOGRAPHE',           'nomMedecin',       'Docteur',           'Docteu',              NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000005', '88888888-0000-0000-0000-000000000001', 'ESPACES_PARASITES',           'medicament',       'Comprime fictif',   '  Comprime fictif ',  NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000006', '88888888-0000-0000-0000-000000000001', 'CASSE_INCOHERENTE',           'pharmacie',        'Pharmacie fictive 1', 'pHARMACIE FICTIVE 1', NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000001', 'CARACTERES_SPECIAUX',         'nomAssure',        'Fictif',            'F#ct@f',              NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000009', '88888888-0000-0000-0000-000000000003', 'FORMAT_DATE_INVALIDE',        'dateOuverture',    '2026-01-05T08:30',  '01/05/2026 8h30',     NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000010', '88888888-0000-0000-0000-000000000003', 'FORMAT_NUMERIQUE_INVALIDE',   'montant',          '4000.00',           '4 000,00 F',          NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000011', '88888888-0000-0000-0000-000000000004', 'FORMAT_IDENTIFIANT_INVALIDE', 'numeroFacture',    'FAC-2026-000004',   'FAC-26-4',            NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000007', '88888888-0000-0000-0000-000000000001', 'VALEUR_NEGATIVE',             'montantTotal',     '10000.00',          '-10000.00',           NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002', NULL,                                   'VALEUR_HORS_LISTE',           'statutCotisation', 'A_JOUR',            'PEUT_ETRE',           NULL),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000012', '88888888-0000-0000-0000-000000000001', 'DOUBLON_EXACT',               NULL,               NULL,                NULL,                  'bbbbbbbb-0000-0000-0000-000000000004'),
 ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000008', '88888888-0000-0000-0000-000000000002', 'CHAMP_VIDE',                  'centreSante',      'CS-T02',            NULL,                  NULL);

-- Chaos, qualite, securite.
INSERT INTO chaos_action (id, run_id, type, parametres, debut, fin_prevue, fin, motif_fin, auteur) VALUES
 ('cccccccc-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'COUPURE_BASE', '{"mode": "DESACTIVER"}',
  '2026-11-01 10:02+00', '2026-11-01 10:03+00', '2026-11-01 10:02:30+00', 'RETABLIE', 'operateur-essai');

INSERT INTO quality_report (id, run_id, version_outil) VALUES
 ('eeeeeeee-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'outil-essai 0.1');
INSERT INTO quality_detection (report_id, run_id, event_id, type, champ, regle, score) VALUES
 ('eeeeeeee-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000004', 'FAUTE_ORTHOGRAPHE', 'nomMedecin', 'dictionnaire', 0.9),
 ('eeeeeeee-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000012', 'DOUBLON_EXACT', NULL, NULL, NULL);

-- Hachages factices : ce ne sont ni un vrai mot de passe ni une vraie cle.
INSERT INTO app_user (id, nom_utilisateur, mot_de_passe_hash, role, mot_de_passe_a_changer, email) VALUES
 ('dddddddd-0000-0000-0000-000000000001', 'operateur-essai', '$2a$10$hachagefactice.hachagefactice.hachagefactice.ab', 'OPERATEUR', FALSE,
  'operateur@exemple.invalid');
INSERT INTO api_key (id, nom, hash) VALUES
 ('dddddddd-0000-0000-0000-000000000002', 'outil-essai', repeat('0', 64));
INSERT INTO audit_log (id, acteur, action, cible, details) VALUES
 ('dddddddd-0000-0000-0000-000000000003', 'operateur-essai', 'RUN_LANCE', '11111111-0000-0000-0000-000000000001', 'jeu d''essai');

-- V19 : raccordement des tables du simulateur aux tables de valeurs de V18.
-- Principe : partout ou une colonne portait un libelle libre ou un code maison, elle porte
-- desormais le code officiel de la table de valeurs correspondante, avec une vraie cle etrangere.
-- Plus aucune liste de valeurs n'est repetee dans une contrainte CHECK : la table de valeurs
-- est la seule autorite. Ajouter une valeur se fait par INSERT, plus par migration de contrainte.

-- 1. Deux specialites manquaient au catalogue, pourtant presentes dans le referentiel simule.
INSERT INTO "TB_REF_SPECIALITES_MEDICALES" (specialite_medicale_code, specialite_medicale_libelle) VALUES
 ('MIN', 'Medecine interne'),
 ('CHD', 'Chirurgie dentaire');

-- 2. Etablissements sanitaires : le type maison devient le code de la pyramide sanitaire,
--    la ville devient le departement. Les quatre types du simulateur se rangent ainsi :
--    HOPITAL -> HG (hopital general), CLINIQUE -> CLI, CENTRE_SANTE -> CSU (centre de sante
--    urbain), CMR -> CHR (centre hospitalier regional, successeur du centre medical regional).
ALTER TABLE "TB_ECHO_CENTRE_SANTE" DROP CONSTRAINT ck_centre_sante_type;
ALTER TABLE "TB_ECHO_CENTRE_SANTE" RENAME COLUMN type TO type_etablissement_code;
ALTER TABLE "TB_ECHO_CENTRE_SANTE" RENAME COLUMN ville TO departement_code;

UPDATE "TB_ECHO_CENTRE_SANTE" SET type_etablissement_code = CASE type_etablissement_code
    WHEN 'HOPITAL'      THEN 'HG'
    WHEN 'CLINIQUE'     THEN 'CLI'
    WHEN 'CENTRE_SANTE' THEN 'CSU'
    WHEN 'CMR'          THEN 'CHR'
    ELSE type_etablissement_code END;

-- Les quinze villes du referentiel simule sont toutes des departements ; le repli sur Abidjan ne
-- sert qu'a garantir que la cle etrangere posee ensuite ne puisse jamais echouer.
UPDATE "TB_ECHO_CENTRE_SANTE" SET departement_code = COALESCE(
    (SELECT d.departement_code FROM "TB_TV_LOCALISATION_DEPARTEMENTS" d
     WHERE d.departement_libelle = departement_code), 'D109');

ALTER TABLE "TB_ECHO_CENTRE_SANTE" ALTER COLUMN type_etablissement_code TYPE VARCHAR(10);
ALTER TABLE "TB_ECHO_CENTRE_SANTE" ALTER COLUMN departement_code       TYPE VARCHAR(10);
ALTER TABLE "TB_ECHO_CENTRE_SANTE"
    ADD CONSTRAINT fk_centre_sante_type_etablissement FOREIGN KEY (type_etablissement_code)
        REFERENCES "TB_TV_TYPES_ETABLISSEMENTS_SANITAIRES" (type_etablissement_code),
    ADD CONSTRAINT fk_centre_sante_departement FOREIGN KEY (departement_code)
        REFERENCES "TB_TV_LOCALISATION_DEPARTEMENTS" (departement_code);

-- 3. Pharmacies : meme traitement pour la ville.
ALTER TABLE "TB_ECHO_PHARMACIE" RENAME COLUMN ville TO departement_code;
UPDATE "TB_ECHO_PHARMACIE" SET departement_code = COALESCE(
    (SELECT d.departement_code FROM "TB_TV_LOCALISATION_DEPARTEMENTS" d
     WHERE d.departement_libelle = departement_code), 'D109');
ALTER TABLE "TB_ECHO_PHARMACIE" ALTER COLUMN departement_code TYPE VARCHAR(10);
ALTER TABLE "TB_ECHO_PHARMACIE"
    ADD CONSTRAINT fk_pharmacie_departement FOREIGN KEY (departement_code)
        REFERENCES "TB_TV_LOCALISATION_DEPARTEMENTS" (departement_code);

-- 4. Medecins : la specialite etait du texte libre, elle devient un code du catalogue.
ALTER TABLE "TB_ECHO_MEDECIN" RENAME COLUMN specialite TO specialite_medicale_code;
-- Conversion explicite des dix libelles libres que le referentiel simule a pu produire. Une
-- jointure sur le libelle ne suffit pas : le catalogue nomme "Radiologie et imagerie medicale"
-- ce que le simulateur ecrivait "Radiologie". Tout libelle inattendu tombe sur MG, et la cle
-- etrangere posee juste apres garantit qu'aucune valeur ne survit hors catalogue.
UPDATE "TB_ECHO_MEDECIN" SET specialite_medicale_code = CASE specialite_medicale_code
    WHEN 'Medecine generale'        THEN 'MG'
    WHEN 'Pediatrie'                THEN 'PED'
    WHEN 'Gynecologie-obstetrique'  THEN 'GYN'
    WHEN 'Chirurgie generale'       THEN 'CHG'
    WHEN 'Cardiologie'              THEN 'CAR'
    WHEN 'Chirurgie dentaire'       THEN 'CHD'
    WHEN 'Ophtalmologie'            THEN 'OPH'
    WHEN 'Radiologie'               THEN 'RAD'
    WHEN 'Biologie medicale'        THEN 'BIO'
    WHEN 'Medecine interne'         THEN 'MIN'
    ELSE COALESCE((SELECT s.specialite_medicale_code FROM "TB_REF_SPECIALITES_MEDICALES" s
                   WHERE s.specialite_medicale_libelle = specialite_medicale_code), 'MG') END;
ALTER TABLE "TB_ECHO_MEDECIN" ALTER COLUMN specialite_medicale_code TYPE VARCHAR(10);
ALTER TABLE "TB_ECHO_MEDECIN"
    ADD CONSTRAINT fk_medecin_specialite FOREIGN KEY (specialite_medicale_code)
        REFERENCES "TB_REF_SPECIALITES_MEDICALES" (specialite_medicale_code);

-- 5. Assures et ayants droit : le genre devient une valeur du catalogue (M, F, I).
--    CHAR(1) devient VARCHAR(1) : une cle etrangere sur du CHAR complete par des espaces.
ALTER TABLE "TB_ECHO_ASSURE"      DROP CONSTRAINT ck_assure_genre;
ALTER TABLE "TB_ECHO_AYANT_DROIT" DROP CONSTRAINT ck_ayant_droit_genre;
ALTER TABLE "TB_ECHO_ASSURE"      ALTER COLUMN genre TYPE VARCHAR(1);
ALTER TABLE "TB_ECHO_AYANT_DROIT" ALTER COLUMN genre TYPE VARCHAR(1);
ALTER TABLE "TB_ECHO_ASSURE"
    ADD CONSTRAINT fk_assure_genre FOREIGN KEY (genre) REFERENCES "TB_TV_GENRES" (genre_code);
ALTER TABLE "TB_ECHO_AYANT_DROIT"
    ADD CONSTRAINT fk_ayant_droit_genre FOREIGN KEY (genre) REFERENCES "TB_TV_GENRES" (genre_code);

-- 6. Factures : le type de bon porte son code officiel de bon de prise en charge, le statut
--    celui du catalogue des statuts. Les CHECK qui repetaient ces listes disparaissent.
ALTER TABLE "TB_ECHO_FACTURE" DROP CONSTRAINT ck_facture_type_bon;
ALTER TABLE "TB_ECHO_FACTURE" DROP CONSTRAINT ck_facture_statut;
ALTER TABLE "TB_ECHO_FACTURE" RENAME COLUMN type_bon TO type_facture_code;

UPDATE "TB_ECHO_FACTURE" SET type_facture_code = CASE type_facture_code
    WHEN 'AMBULATOIRE'       THEN 'ER-01-GPR'
    WHEN 'HOSPITALISATION'   THEN 'ER-02-GPR'
    WHEN 'BIOLOGIE_IMAGERIE' THEN 'ER-03-GPR'
    ELSE type_facture_code END;

ALTER TABLE "TB_ECHO_FACTURE"
    ADD CONSTRAINT fk_facture_type FOREIGN KEY (type_facture_code)
        REFERENCES "TB_TV_TYPES_FACTURES" (type_facture_code),
    ADD CONSTRAINT fk_facture_statut FOREIGN KEY (statut)
        REFERENCES "TB_TV_STATUTS_FACTURES" (statut_code);

-- 7. Historique des statuts : meme autorite, y compris pour l'ancien statut qui peut etre nul.
ALTER TABLE "TB_ECHO_FACTURE_HISTORIQUE" DROP CONSTRAINT ck_facture_historique_ancien;
ALTER TABLE "TB_ECHO_FACTURE_HISTORIQUE" DROP CONSTRAINT ck_facture_historique_nouveau;
ALTER TABLE "TB_ECHO_FACTURE_HISTORIQUE"
    ADD CONSTRAINT fk_facture_historique_ancien FOREIGN KEY (ancien_statut)
        REFERENCES "TB_TV_STATUTS_FACTURES" (statut_code),
    ADD CONSTRAINT fk_facture_historique_nouveau FOREIGN KEY (nouveau_statut)
        REFERENCES "TB_TV_STATUTS_FACTURES" (statut_code);

-- 8. Regime de l'assure. Tout beneficiaire de la CMU releve de l'un des deux regimes : le regime
--    general de base, contributif, ou le regime d'assistance medicale pour les personnes
--    economiquement faibles. La colonne rend TB_TV_REGIMES effectivement utilisee.
--    ATTENTION : le taux par defaut du regime n'est PAS encore applique par la tarification, qui
--    continue de lire "TB_ECHO_TAUX" par type de prestation. Les deux axes sont independants et
--    leur articulation doit etre tranchee par les directions metier.
ALTER TABLE "TB_ECHO_ASSURE" ADD COLUMN regime_code VARCHAR(10) NOT NULL DEFAULT 'RGB';
ALTER TABLE "TB_ECHO_ASSURE" ALTER COLUMN regime_code DROP DEFAULT;
ALTER TABLE "TB_ECHO_ASSURE"
    ADD CONSTRAINT fk_assure_regime FOREIGN KEY (regime_code) REFERENCES "TB_TV_REGIMES" (regime_code);

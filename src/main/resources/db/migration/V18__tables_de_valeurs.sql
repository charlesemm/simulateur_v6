-- V18 : les tables de valeurs (TB_TV_*) et le referentiel des specialites medicales.
--
-- Convention reprise de la base cible : une table par liste, prefixe TB_TV_ pour les tables de valeurs,
-- TB_REF_ pour les referentiels. Noms de tables entre guillemets et en majuscules (comme V17),
-- colonnes en minuscules snake_case (convention du projet).
--
-- Chaque table porte le bloc d'audit commun a toutes les tables CNAM
-- (date_creation, utilisateur_id_creation, date_modification, utilisateur_id_modification)
-- et une periode de validite (date_debut, date_fin) : un code peut etre perime a la date des soins,
-- ce qui est une anomalie temporelle a part entiere.
--
-- Pourquoi ces tables : l'anomalie VALEUR_HORS_LISTE consiste a ecrire dans une colonne *_CODE une
-- valeur absente de la liste autorisee. Sans les listes, elle n'a rien contre quoi se mesurer.
--
-- Origine des valeurs :
--   LOCALISATION  : decoupage administratif reel (14 districts, 31 regions, 111 departements).
--   REGIMES       : RGB et RAM, les deux regimes reels de la CMU. Ticket moderateur RGB de 30 %.
--   SPECIALITES   : specialites exercees en Cote d'Ivoire.
--   ETABLISSEMENTS: pyramide sanitaire (criteres et normes d'implantation, ministere de la Sante, 2010).
--   TYPES_FACTURES, PRODUITS, STATUTS, MOTIFS_REJETS : deduits, a faire valider par les directions metier.
--
-- Sept de ces tables (CIVILITES, GENRES, PRODUITS, ORGANISMES, STATUTS_FACTURES, MOTIFS_REJETS,
-- TYPES_ETABLISSEMENTS_SANITAIRES) n'apparaissent pas dans la liste cible : elles sont necessaires au
-- fonctionnement du simulateur, qui doit connaitre la liste autorisee pour pouvoir en sortir.
--
-- Aucune donnee reelle d'assure : ce sont des nomenclatures publiques.
-- Pas d'index ici : ils vont dans le script d'index dedie.

-- ===========================================================================
-- 1. LOCALISATION : pays -> regions -> departements -> localites
-- ===========================================================================

CREATE TABLE "TB_TV_LOCALISATION_PAYS" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    pays_code                   CHAR(3)      NOT NULL,
    pays_code_iso2              CHAR(2)      NOT NULL,
    pays_libelle                VARCHAR(100) NOT NULL,
    pays_statut                 VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_pays PRIMARY KEY (pays_code),
    CONSTRAINT uq_tv_pays_iso2 UNIQUE (pays_code_iso2),
    CONSTRAINT ck_tv_pays_statut CHECK (pays_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_pays_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Cote d'Ivoire, les 14 autres Etats de la CEDEAO, et les nationalites les plus
-- representees parmi les residents. Liste volontairement courte : elle se complete
-- par un nouveau script sans toucher au code.
INSERT INTO "TB_TV_LOCALISATION_PAYS" (pays_code, pays_code_iso2, pays_libelle) VALUES
 ('CIV', 'CI', 'Cote d''Ivoire'),
 ('BEN', 'BJ', 'Benin'),
 ('BFA', 'BF', 'Burkina Faso'),
 ('CPV', 'CV', 'Cap-Vert'),
 ('GMB', 'GM', 'Gambie'),
 ('GHA', 'GH', 'Ghana'),
 ('GIN', 'GN', 'Guinee'),
 ('GNB', 'GW', 'Guinee-Bissau'),
 ('LBR', 'LR', 'Liberia'),
 ('MLI', 'ML', 'Mali'),
 ('NER', 'NE', 'Niger'),
 ('NGA', 'NG', 'Nigeria'),
 ('SEN', 'SN', 'Senegal'),
 ('SLE', 'SL', 'Sierra Leone'),
 ('TGO', 'TG', 'Togo'),
 ('CMR', 'CM', 'Cameroun'),
 ('TCD', 'TD', 'Tchad'),
 ('COG', 'CG', 'Congo'),
 ('GAB', 'GA', 'Gabon'),
 ('MAR', 'MA', 'Maroc'),
 ('TUN', 'TN', 'Tunisie'),
 ('DZA', 'DZ', 'Algerie'),
 ('MRT', 'MR', 'Mauritanie'),
 ('LBN', 'LB', 'Liban'),
 ('SYR', 'SY', 'Syrie'),
 ('FRA', 'FR', 'France'),
 ('CHN', 'CN', 'Chine'),
 ('IND', 'IN', 'Inde');

CREATE TABLE "TB_TV_LOCALISATION_REGIONS" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    region_code                 VARCHAR(10)  NOT NULL,
    region_libelle              VARCHAR(100) NOT NULL,
    -- le district n'a pas de table propre dans la cible : il reste un attribut de la region.
    district_libelle            VARCHAR(100) NOT NULL,
    region_chef_lieu            VARCHAR(100) NOT NULL,
    pays_code                   CHAR(3)      NOT NULL REFERENCES "TB_TV_LOCALISATION_PAYS" (pays_code),
    region_statut               VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_regions PRIMARY KEY (region_code),
    CONSTRAINT ck_tv_regions_statut CHECK (region_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_regions_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- 31 regions reparties dans 12 districts, plus les 2 districts autonomes d'Abidjan et de
-- Yamoussoukro, qui ne sont pas decoupes en regions mais portent directement des departements.
INSERT INTO "TB_TV_LOCALISATION_REGIONS" (region_code, region_libelle, district_libelle, region_chef_lieu, pays_code) VALUES
 ('R01', 'Agneby-Tiassa',     'Lagunes',            'Agboville',      'CIV'),
 ('R02', 'Bafing',            'Woroba',             'Touba',          'CIV'),
 ('R03', 'Bagoue',            'Savanes',            'Boundiali',      'CIV'),
 ('R04', 'Belier',            'Lacs',               'Yamoussoukro',   'CIV'),
 ('R05', 'Bere',              'Woroba',             'Mankono',        'CIV'),
 ('R06', 'Bounkani',          'Zanzan',             'Bouna',          'CIV'),
 ('R07', 'Cavally',           'Montagnes',          'Guiglo',         'CIV'),
 ('R08', 'Folon',             'Denguele',           'Minignan',       'CIV'),
 ('R09', 'Gbeke',             'Vallee du Bandama',  'Bouake',         'CIV'),
 ('R10', 'Gbokle',            'Bas-Sassandra',      'Sassandra',      'CIV'),
 ('R11', 'Goh',               'Goh-Djiboua',        'Gagnoa',         'CIV'),
 ('R12', 'Gontougo',          'Zanzan',             'Bondoukou',      'CIV'),
 ('R13', 'Grands-Ponts',      'Lagunes',            'Dabou',          'CIV'),
 ('R14', 'Guemon',            'Montagnes',          'Duekoue',        'CIV'),
 ('R15', 'Hambol',            'Vallee du Bandama',  'Katiola',        'CIV'),
 ('R16', 'Haut-Sassandra',    'Sassandra-Marahoue', 'Daloa',          'CIV'),
 ('R17', 'Iffou',             'Lacs',               'Daoukro',        'CIV'),
 ('R18', 'Indenie-Djuablin',  'Comoe',              'Abengourou',     'CIV'),
 ('R19', 'Kabadougou',        'Denguele',           'Odienne',        'CIV'),
 ('R20', 'La Me',             'Lagunes',            'Adzope',         'CIV'),
 ('R21', 'Loh-Djiboua',       'Goh-Djiboua',        'Divo',           'CIV'),
 ('R22', 'Marahoue',          'Sassandra-Marahoue', 'Bouafle',        'CIV'),
 ('R23', 'Moronou',           'Lacs',               'Bongouanou',     'CIV'),
 ('R24', 'Nawa',              'Bas-Sassandra',      'Soubre',         'CIV'),
 ('R25', 'N''Zi',             'Lacs',               'Dimbokro',       'CIV'),
 ('R26', 'Poro',              'Savanes',            'Korhogo',        'CIV'),
 ('R27', 'San-Pedro',         'Bas-Sassandra',      'San-Pedro',      'CIV'),
 ('R28', 'Sud-Comoe',         'Comoe',              'Aboisso',        'CIV'),
 ('R29', 'Tchologo',          'Savanes',            'Ferkessedougou', 'CIV'),
 ('R30', 'Tonkpi',            'Montagnes',          'Man',            'CIV'),
 ('R31', 'Worodougou',        'Woroba',             'Seguela',        'CIV'),
 ('R32', 'Abidjan',           'Abidjan',            'Abidjan',        'CIV'),
 ('R33', 'Yamoussoukro',      'Yamoussoukro',       'Yamoussoukro',   'CIV');

CREATE TABLE "TB_TV_LOCALISATION_DEPARTEMENTS" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    departement_code            VARCHAR(10)  NOT NULL,
    departement_libelle         VARCHAR(100) NOT NULL,
    region_code                 VARCHAR(10)  NOT NULL REFERENCES "TB_TV_LOCALISATION_REGIONS" (region_code),
    departement_statut          VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_departements PRIMARY KEY (departement_code),
    CONSTRAINT ck_tv_departements_statut CHECK (departement_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_departements_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- 111 departements.
INSERT INTO "TB_TV_LOCALISATION_DEPARTEMENTS" (departement_code, departement_libelle, region_code) VALUES
 ('D001', 'Agboville', 'R01'),
 ('D002', 'Sikensi', 'R01'),
 ('D003', 'Taabo', 'R01'),
 ('D004', 'Tiassale', 'R01'),
 ('D005', 'Koro', 'R02'),
 ('D006', 'Ouaninou', 'R02'),
 ('D007', 'Touba', 'R02'),
 ('D008', 'Boundiali', 'R03'),
 ('D009', 'Kouto', 'R03'),
 ('D010', 'Tengrela', 'R03'),
 ('D011', 'Didievi', 'R04'),
 ('D012', 'Djekanou', 'R04'),
 ('D013', 'Tiebissou', 'R04'),
 ('D014', 'Toumodi', 'R04'),
 ('D015', 'Dianra', 'R05'),
 ('D016', 'Kounahiri', 'R05'),
 ('D017', 'Mankono', 'R05'),
 ('D018', 'Bouna', 'R06'),
 ('D019', 'Doropo', 'R06'),
 ('D020', 'Nassian', 'R06'),
 ('D021', 'Tehini', 'R06'),
 ('D022', 'Blolequin', 'R07'),
 ('D023', 'Guiglo', 'R07'),
 ('D024', 'Tai', 'R07'),
 ('D025', 'Toulepleu', 'R07'),
 ('D026', 'Kaniasso', 'R08'),
 ('D027', 'Minignan', 'R08'),
 ('D028', 'Beoumi', 'R09'),
 ('D029', 'Botro', 'R09'),
 ('D030', 'Bouake', 'R09'),
 ('D031', 'Sakassou', 'R09'),
 ('D032', 'Fresco', 'R10'),
 ('D033', 'Sassandra', 'R10'),
 ('D034', 'Gagnoa', 'R11'),
 ('D035', 'Oume', 'R11'),
 ('D036', 'Bondoukou', 'R12'),
 ('D037', 'Koun-Fao', 'R12'),
 ('D038', 'Sandegue', 'R12'),
 ('D039', 'Tanda', 'R12'),
 ('D040', 'Transua', 'R12'),
 ('D041', 'Dabou', 'R13'),
 ('D042', 'Grand-Lahou', 'R13'),
 ('D043', 'Jacqueville', 'R13'),
 ('D044', 'Bangolo', 'R14'),
 ('D045', 'Duekoue', 'R14'),
 ('D046', 'Facobly', 'R14'),
 ('D047', 'Kouibly', 'R14'),
 ('D048', 'Dabakala', 'R15'),
 ('D049', 'Katiola', 'R15'),
 ('D050', 'Niakaramadougou', 'R15'),
 ('D051', 'Daloa', 'R16'),
 ('D052', 'Issia', 'R16'),
 ('D053', 'Vavoua', 'R16'),
 ('D054', 'Zoukougbeu', 'R16'),
 ('D055', 'Daoukro', 'R17'),
 ('D056', 'M''Bahiakro', 'R17'),
 ('D057', 'Ouelle', 'R17'),
 ('D058', 'Prikro', 'R17'),
 ('D059', 'Abengourou', 'R18'),
 ('D060', 'Agnibilekrou', 'R18'),
 ('D061', 'Bettie', 'R18'),
 ('D062', 'Gbeleban', 'R19'),
 ('D063', 'Madinani', 'R19'),
 ('D064', 'Odienne', 'R19'),
 ('D065', 'Samatiguila', 'R19'),
 ('D066', 'Seguelon', 'R19'),
 ('D067', 'Adzope', 'R20'),
 ('D068', 'Akoupe', 'R20'),
 ('D069', 'Alepe', 'R20'),
 ('D070', 'Yakasse-Attobrou', 'R20'),
 ('D071', 'Divo', 'R21'),
 ('D072', 'Guitry', 'R21'),
 ('D073', 'Lakota', 'R21'),
 ('D074', 'Bonon', 'R22'),
 ('D075', 'Bouafle', 'R22'),
 ('D076', 'Gohitafla', 'R22'),
 ('D077', 'Sinfra', 'R22'),
 ('D078', 'Zuenoula', 'R22'),
 ('D079', 'Arrah', 'R23'),
 ('D080', 'Bongouanou', 'R23'),
 ('D081', 'M''Batto', 'R23'),
 ('D082', 'Buyo', 'R24'),
 ('D083', 'Gueyo', 'R24'),
 ('D084', 'Meagui', 'R24'),
 ('D085', 'Soubre', 'R24'),
 ('D086', 'Bocanda', 'R25'),
 ('D087', 'Dimbokro', 'R25'),
 ('D088', 'Kouassi-Kouassikro', 'R25'),
 ('D089', 'Dikodougou', 'R26'),
 ('D090', 'Korhogo', 'R26'),
 ('D091', 'M''Bengue', 'R26'),
 ('D092', 'Sinematiali', 'R26'),
 ('D093', 'San-Pedro', 'R27'),
 ('D094', 'Tabou', 'R27'),
 ('D095', 'Aboisso', 'R28'),
 ('D096', 'Adiake', 'R28'),
 ('D097', 'Grand-Bassam', 'R28'),
 ('D098', 'Tiapoum', 'R28'),
 ('D099', 'Ferkessedougou', 'R29'),
 ('D100', 'Kong', 'R29'),
 ('D101', 'Ouangolodougou', 'R29'),
 ('D102', 'Biankouma', 'R30'),
 ('D103', 'Danane', 'R30'),
 ('D104', 'Man', 'R30'),
 ('D105', 'Sipilou', 'R30'),
 ('D106', 'Zouan-Hounien', 'R30'),
 ('D107', 'Kani', 'R31'),
 ('D108', 'Seguela', 'R31'),
 ('D109', 'Abidjan', 'R32'),
 ('D110', 'Attiegouakro', 'R33'),
 ('D111', 'Yamoussoukro', 'R33');

CREATE TABLE "TB_TV_LOCALISATION_LOCALITES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    localite_code               VARCHAR(10)  NOT NULL,
    localite_libelle            VARCHAR(100) NOT NULL,
    departement_code            VARCHAR(10)  NOT NULL REFERENCES "TB_TV_LOCALISATION_DEPARTEMENTS" (departement_code),
    localite_statut             VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_localites PRIMARY KEY (localite_code),
    CONSTRAINT ck_tv_localites_statut CHECK (localite_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_localites_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Les 13 communes du district autonome d'Abidjan, ou se concentre l'essentiel de l'offre de soins,
-- plus les chefs-lieux des plus grandes villes de l'interieur. Les 510 sous-prefectures ne sont pas
-- seedees ici : elles se completent par un nouveau script si le besoin apparait.
INSERT INTO "TB_TV_LOCALISATION_LOCALITES" (localite_code, localite_libelle, departement_code) VALUES
 ('L001', 'Abobo',        'D109'),
 ('L002', 'Adjame',       'D109'),
 ('L003', 'Attecoube',    'D109'),
 ('L004', 'Cocody',       'D109'),
 ('L005', 'Koumassi',     'D109'),
 ('L006', 'Marcory',      'D109'),
 ('L007', 'Le Plateau',   'D109'),
 ('L008', 'Port-Bouet',   'D109'),
 ('L009', 'Treichville',  'D109'),
 ('L010', 'Yopougon',     'D109'),
 ('L011', 'Anyama',       'D109'),
 ('L012', 'Bingerville',  'D109'),
 ('L013', 'Songon',       'D109'),
 ('L014', 'Bouake',       'D030'),
 ('L015', 'Daloa',        'D051'),
 ('L016', 'Korhogo',      'D091'),
 ('L017', 'San-Pedro',    'D093'),
 ('L018', 'Yamoussoukro', 'D111'),
 ('L019', 'Man',          'D104'),
 ('L020', 'Gagnoa',       'D034'),
 ('L021', 'Abengourou',   'D059'),
 ('L022', 'Divo',         'D070'),
 ('L023', 'Grand-Bassam', 'D097'),
 ('L024', 'Soubre',       'D085');

-- ===========================================================================
-- 2. REGIMES et TYPES DE FACTURES
-- ===========================================================================

CREATE TABLE "TB_TV_REGIMES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    regime_code                 VARCHAR(10)   NOT NULL,
    regime_libelle              VARCHAR(200)  NOT NULL,
    -- taux de prise en charge par defaut, en pourcentage. Repris dans TB_FACTURES.REGIME_TAUX.
    -- NUMERIC et non double : c'est de l'argent, meme exprime en taux.
    regime_taux_defaut          NUMERIC(5, 2) NOT NULL,
    regime_contributif          BOOLEAN       NOT NULL,
    regime_statut               VARCHAR(10)   NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE          NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_regimes PRIMARY KEY (regime_code),
    CONSTRAINT ck_tv_regimes_taux CHECK (regime_taux_defaut >= 0 AND regime_taux_defaut <= 100),
    CONSTRAINT ck_tv_regimes_statut CHECK (regime_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_regimes_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Les deux regimes reels de la CMU. RGB : ticket moderateur de 30 %, donc 70 % de prise en charge.
-- RAM : l'Etat se substitue a l'assure pour la cotisation et le ticket moderateur, donc 100 %.
INSERT INTO "TB_TV_REGIMES" (regime_code, regime_libelle, regime_taux_defaut, regime_contributif) VALUES
 ('RGB', 'Regime General de Base',        70.00, TRUE),
 ('RAM', 'Regime d''Assistance Medicale', 100.00, FALSE);

CREATE TABLE "TB_TV_TYPES_FACTURES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    type_facture_code           VARCHAR(20)  NOT NULL,
    type_facture_libelle        VARCHAR(200) NOT NULL,
    type_facture_statut         VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_types_factures PRIMARY KEY (type_facture_code),
    CONSTRAINT ck_tv_types_factures_statut CHECK (type_facture_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_types_factures_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Les quatre bons de prise en charge du cahier des charges. Les codes ER-0x-GPR viennent de la CNAM :
-- aucune source publique n'en donne la signification, ils sont repris tels quels.
-- Le quatrieme n'a pas de code connu : DENTAIRE est provisoire.
INSERT INTO "TB_TV_TYPES_FACTURES" (type_facture_code, type_facture_libelle) VALUES
 ('ER-01-GPR', 'Ambulatoire et medicaments'),
 ('ER-02-GPR', 'Hospitalisation'),
 ('ER-03-GPR', 'Biologie et imagerie'),
 ('DENTAIRE',  'Soins dentaires');

-- ===========================================================================
-- 3. SPECIALITES MEDICALES (referentiel, pas table de valeurs)
-- ===========================================================================

CREATE TABLE "TB_REF_SPECIALITES_MEDICALES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    specialite_medicale_code    VARCHAR(10)  NOT NULL,
    specialite_medicale_libelle VARCHAR(200) NOT NULL,
    specialite_medicale_statut  VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_ref_specialites PRIMARY KEY (specialite_medicale_code),
    CONSTRAINT ck_ref_specialites_statut CHECK (specialite_medicale_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_ref_specialites_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

INSERT INTO "TB_REF_SPECIALITES_MEDICALES" (specialite_medicale_code, specialite_medicale_libelle) VALUES
 ('MG',  'Medecine generale'),
 ('PED', 'Pediatrie'),
 ('GYN', 'Gynecologie-obstetrique'),
 ('CHG', 'Chirurgie generale'),
 ('ORT', 'Orthopedie-traumatologie'),
 ('INF', 'Maladies infectieuses'),
 ('PNE', 'Pneumologie'),
 ('GAS', 'Gastro-enterologie'),
 ('NEP', 'Nephrologie'),
 ('URO', 'Urologie'),
 ('NEU', 'Neurologie'),
 ('PSY', 'Psychiatrie'),
 ('DER', 'Dermatologie'),
 ('OPH', 'Ophtalmologie'),
 ('ORL', 'Oto-rhino-laryngologie'),
 ('RHU', 'Rhumatologie'),
 ('ONC', 'Oncologie medicale'),
 ('ODO', 'Odontostomatologie'),
 ('RAD', 'Radiologie et imagerie medicale'),
 ('BIO', 'Biologie medicale'),
 ('PHA', 'Pharmacie'),
 ('SF',  'Sage-femme'),
 ('KIN', 'Kinesitherapie');

-- Cardiologie, diabetologie et anesthesie-reanimation ont ete ajoutees au perimetre CMU en mars 2023 :
-- leur date_debut le reflete, ce qui rend testable l'anomalie « code perime a la date des soins »
-- dans l'autre sens : un acte de cardiologie date d'avant mars 2023.
INSERT INTO "TB_REF_SPECIALITES_MEDICALES" (specialite_medicale_code, specialite_medicale_libelle, date_debut) VALUES
 ('CAR', 'Cardiologie',                 DATE '2023-03-01'),
 ('DIA', 'Diabetologie-endocrinologie', DATE '2023-03-01'),
 ('ANR', 'Anesthesie-reanimation',      DATE '2023-03-01');

-- ===========================================================================
-- 4. Les sept listes hors capture, necessaires au fonctionnement du simulateur
-- ===========================================================================

CREATE TABLE "TB_TV_CIVILITES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    civilite_code               VARCHAR(10)  NOT NULL,
    civilite_libelle            VARCHAR(100) NOT NULL,
    -- ASSURE ou PROFESSIONNEL : un assure porteur de la civilite PR est une anomalie en soi.
    civilite_portee             VARCHAR(20)  NOT NULL,
    civilite_statut             VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_civilites PRIMARY KEY (civilite_code),
    CONSTRAINT ck_tv_civilites_portee CHECK (civilite_portee IN ('ASSURE', 'PROFESSIONNEL')),
    CONSTRAINT ck_tv_civilites_statut CHECK (civilite_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_civilites_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

INSERT INTO "TB_TV_CIVILITES" (civilite_code, civilite_libelle, civilite_portee) VALUES
 ('M',    'Monsieur',     'ASSURE'),
 ('MME',  'Madame',       'ASSURE'),
 ('MLLE', 'Mademoiselle', 'ASSURE'),
 ('ENF',  'Enfant',       'ASSURE'),
 ('DR',   'Docteur',      'PROFESSIONNEL'),
 ('PR',   'Professeur',   'PROFESSIONNEL');

CREATE TABLE "TB_TV_GENRES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    genre_code                  CHAR(1)      NOT NULL,
    genre_libelle               VARCHAR(100) NOT NULL,
    genre_statut                VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_genres PRIMARY KEY (genre_code),
    CONSTRAINT ck_tv_genres_statut CHECK (genre_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_genres_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- I : indetermine, cas reel du nouveau-ne dont l'etat civil n'est pas encore etabli.
-- Se croise avec LISTE_GENRES des referentiels actes et medicaments.
INSERT INTO "TB_TV_GENRES" (genre_code, genre_libelle) VALUES
 ('M', 'Masculin'),
 ('F', 'Feminin'),
 ('I', 'Indetermine');

CREATE TABLE "TB_TV_PRODUITS" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    produit_code                VARCHAR(20)  NOT NULL,
    produit_libelle             VARCHAR(200) NOT NULL,
    produit_statut              VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_produits PRIMARY KEY (produit_code),
    CONSTRAINT ck_tv_produits_statut CHECK (produit_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_produits_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- DEDUIT, a faire valider : aucune source publique ne dit ce que PRODUIT_CODE contient reellement.
INSERT INTO "TB_TV_PRODUITS" (produit_code, produit_libelle) VALUES
 ('PSB',  'Panier de soins de base CMU'),
 ('GRAT', 'Gratuite ciblee financee par l''Etat'),
 ('PSC',  'Panier complementaire');

CREATE TABLE "TB_TV_ORGANISMES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    organisme_code              VARCHAR(20)  NOT NULL,
    organisme_libelle           VARCHAR(200) NOT NULL,
    organisme_role              VARCHAR(20)  NOT NULL,
    organisme_statut            VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_organismes PRIMARY KEY (organisme_code),
    CONSTRAINT ck_tv_organismes_role CHECK (organisme_role IN ('GESTIONNAIRE', 'TUTELLE', 'OGD')),
    CONSTRAINT ck_tv_organismes_statut CHECK (organisme_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_organismes_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Le circuit decrit par la DG-CMU transmet le bordereau de facturation a la CNAM ou a l'OGD.
INSERT INTO "TB_TV_ORGANISMES" (organisme_code, organisme_libelle, organisme_role) VALUES
 ('CNAM',    'Caisse Nationale d''Assurance Maladie',                     'GESTIONNAIRE'),
 ('DGCMU',   'Direction Generale de la Couverture Maladie Universelle',   'TUTELLE'),
 ('MUGEFCI', 'Mutuelle Generale des Fonctionnaires et Agents de l''Etat', 'OGD'),
 ('CNPS',    'Caisse Nationale de Prevoyance Sociale',                    'OGD'),
 ('CGRAE',   'Caisse Generale de Retraite des Agents de l''Etat',         'OGD');

CREATE TABLE "TB_TV_TYPES_ETABLISSEMENTS_SANITAIRES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    type_etablissement_code     VARCHAR(20)  NOT NULL,
    type_etablissement_libelle  VARCHAR(200) NOT NULL,
    -- niveau de la pyramide sanitaire : conditionne le parcours de soins.
    type_etablissement_niveau   VARCHAR(20)  NOT NULL,
    type_etablissement_statut   VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_types_etablissements PRIMARY KEY (type_etablissement_code),
    CONSTRAINT ck_tv_types_etablissements_niveau
        CHECK (type_etablissement_niveau IN ('PRIMAIRE', 'SECONDAIRE', 'TERTIAIRE', 'PRIVE')),
    CONSTRAINT ck_tv_types_etablissements_statut CHECK (type_etablissement_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_types_etablissements_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- Pyramide sanitaire ivoirienne : les ESPC se repartissent en CSR, CSU, CSUS et FSU
-- (criteres et normes d'implantation, ministere de la Sante, 2010). Le dispensaire et la maternite
-- ne sont pas des types : ce sont les composantes d'un CSR ou d'un CSU.
INSERT INTO "TB_TV_TYPES_ETABLISSEMENTS_SANITAIRES" (type_etablissement_code, type_etablissement_libelle, type_etablissement_niveau) VALUES
 ('CSR',  'Centre de Sante Rural',              'PRIMAIRE'),
 ('CSU',  'Centre de Sante Urbain',             'PRIMAIRE'),
 ('CSUS', 'Centre de Sante Urbain Specialise',  'PRIMAIRE'),
 ('FSU',  'Formation Sanitaire Urbaine',        'PRIMAIRE'),
 ('HG',   'Hopital General',                    'SECONDAIRE'),
 ('CHR',  'Centre Hospitalier Regional',        'SECONDAIRE'),
 ('CHS',  'Centre Hospitalier Specialise',      'SECONDAIRE'),
 ('CHU',  'Centre Hospitalier Universitaire',   'TERTIAIRE'),
 ('INS',  'Institut National Specialise',       'TERTIAIRE'),
 ('POL',  'Polyclinique',                       'PRIVE'),
 ('CLI',  'Clinique',                           'PRIVE'),
 ('CAB',  'Cabinet medical',                    'PRIVE'),
 ('CBD',  'Cabinet dentaire',                   'PRIVE'),
 ('INFP', 'Infirmerie privee',                  'PRIVE'),
 ('OFF',  'Officine de pharmacie',              'PRIVE'),
 ('LAB',  'Laboratoire d''analyses medicales',  'PRIVE'),
 ('CIM',  'Centre d''imagerie medicale',        'PRIVE');

CREATE TABLE "TB_TV_STATUTS_FACTURES" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    statut_code                 VARCHAR(30)  NOT NULL,
    statut_libelle              VARCHAR(200) NOT NULL,
    -- PARCOURS : ce que vit la facture au centre de sante (machine a etats FactureStatut).
    -- LIQUIDATION : sa vie apres transmission a l'organisme. DEDUIT, a faire valider.
    statut_famille              VARCHAR(20)  NOT NULL,
    statut_ordre                SMALLINT     NOT NULL,
    statut_statut               VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_statuts_factures PRIMARY KEY (statut_code),
    CONSTRAINT ck_tv_statuts_factures_famille CHECK (statut_famille IN ('PARCOURS', 'LIQUIDATION')),
    CONSTRAINT ck_tv_statuts_factures_statut CHECK (statut_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_statuts_factures_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

INSERT INTO "TB_TV_STATUTS_FACTURES" (statut_code, statut_libelle, statut_famille, statut_ordre) VALUES
 ('OUVERTE',              'Facture ouverte',                   'PARCOURS',    1),
 ('EN_COURS',             'Soins en cours',                    'PARCOURS',    2),
 ('PRESCRITE',            'Prescription emise',                'PARCOURS',    3),
 ('MEDICAMENTS_DELIVRES', 'Medicaments delivres',              'PARCOURS',    4),
 ('FINALISEE',            'Facture finalisee',                 'PARCOURS',    5),
 ('REFUSEE',              'Acces refuse, droits non ouverts',  'PARCOURS',    6),
 ('ABANDONNEE',           'Parcours abandonne par le patient', 'PARCOURS',    7),
 ('TRANSMISE',            'Transmise a l''organisme',          'LIQUIDATION', 8),
 ('RECUE',                'Recue par l''organisme',            'LIQUIDATION', 9),
 ('EN_LIQUIDATION',       'En cours de liquidation',           'LIQUIDATION', 10),
 ('LIQUIDEE',             'Liquidee, montant arrete',          'LIQUIDATION', 11),
 ('REJETEE',              'Rejetee en liquidation',            'LIQUIDATION', 12),
 ('SUSPENDUE',            'Suspendue pour piece ou controle',  'LIQUIDATION', 13),
 ('PAYEE',                'Payee au prestataire',              'LIQUIDATION', 14),
 ('ANNULEE',              'Annulee',                           'LIQUIDATION', 15);

CREATE TABLE "TB_TV_MOTIFS_REJETS" (
    date_creation               TIMESTAMP   NOT NULL DEFAULT now(),
    utilisateur_id_creation     VARCHAR(50) NOT NULL DEFAULT 'SIMULATEUR',
    date_modification           TIMESTAMP,
    utilisateur_id_modification VARCHAR(50),
    motif_rejet_code            VARCHAR(10)  NOT NULL,
    motif_rejet_libelle         VARCHAR(200) NOT NULL,
    motif_rejet_famille         VARCHAR(20)  NOT NULL,
    motif_rejet_statut          VARCHAR(10)  NOT NULL DEFAULT 'ACTIF',
    date_debut                  DATE         NOT NULL DEFAULT DATE '2019-10-01',
    date_fin                    DATE,
    CONSTRAINT pk_tv_motifs_rejets PRIMARY KEY (motif_rejet_code),
    CONSTRAINT ck_tv_motifs_rejets_famille CHECK (motif_rejet_famille IN (
        'DROITS', 'PRISE_EN_CHARGE', 'NOMENCLATURE', 'ELIGIBILITE', 'PRESTATAIRE', 'FACTURE')),
    CONSTRAINT ck_tv_motifs_rejets_statut CHECK (motif_rejet_statut IN ('ACTIF', 'INACTIF')),
    CONSTRAINT ck_tv_motifs_rejets_periode CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

-- DEDUIT, a faire valider. Chaque motif correspond a un controle que le simulateur sait declencher :
-- la famille ELIGIBILITE rejoue les bornes d'age, de genre, de quantite et le statut hapax portes
-- par TB_REF_ACTES_MEDICAUX et TB_REF_MEDICAMENTS.
INSERT INTO "TB_TV_MOTIFS_REJETS" (motif_rejet_code, motif_rejet_libelle, motif_rejet_famille) VALUES
 ('DRT01', 'Droits non ouverts a la date des soins',     'DROITS'),
 ('DRT02', 'Cotisations non a jour',                     'DROITS'),
 ('DRT03', 'Beneficiaire decede a la date des soins',    'DROITS'),
 ('DRT04', 'Assure inconnu ou identifiant introuvable',  'DROITS'),
 ('PEC01', 'Bon de prise en charge absent ou invalide',  'PRISE_EN_CHARGE'),
 ('PEC02', 'Entente prealable manquante',                'PRISE_EN_CHARGE'),
 ('PEC03', 'Parcours de soins non respecte',             'PRISE_EN_CHARGE'),
 ('NOM01', 'Acte absent de la nomenclature',             'NOMENCLATURE'),
 ('NOM02', 'Medicament non remboursable',                'NOMENCLATURE'),
 ('NOM03', 'Acte non autorise pour ce type de facture',  'NOMENCLATURE'),
 ('NOM04', 'Code perime a la date des soins',            'NOMENCLATURE'),
 ('ELG01', 'Age hors des bornes autorisees pour l''acte', 'ELIGIBILITE'),
 ('ELG02', 'Acte non autorise pour ce genre',            'ELIGIBILITE'),
 ('ELG03', 'Quantite superieure au maximum autorise',    'ELIGIBILITE'),
 ('ELG04', 'Acte a usage unique deja servi',             'ELIGIBILITE'),
 ('PRS01', 'Professionnel de sante non agree',           'PRESTATAIRE'),
 ('PRS02', 'Specialite non habilitee pour cet acte',     'PRESTATAIRE'),
 ('PRS03', 'Etablissement non conventionne',             'PRESTATAIRE'),
 ('FAC01', 'Facture en double',                          'FACTURE'),
 ('FAC02', 'Facture hors delai de transmission',         'FACTURE'),
 ('FAC03', 'Incoherence de montants',                    'FACTURE'),
 ('FAC04', 'Piece justificative manquante',              'FACTURE');

-- V5 : taux de prise en charge, modifiables sans recompiler (UPDATE puis rechargement de la regle).
-- Valeurs FICTIVES de simulation : les taux reels sont fixes par les directions metier.
-- Un taux CNAM par type de prestation ; le taux complementaire s'applique au ticket moderateur
-- (ce qui reste apres la part CNAM). Pas d'index : 6 lignes.

CREATE TABLE taux (
    code         VARCHAR(40)  PRIMARY KEY,
    taux         NUMERIC(5,4) NOT NULL,
    description  VARCHAR(200) NOT NULL,
    CONSTRAINT ck_taux_borne CHECK (taux >= 0 AND taux <= 1),
    CONSTRAINT ck_taux_code CHECK (code IN ('CNAM_CONSULTATION', 'CNAM_EXAMEN', 'CNAM_HOSPITALISATION',
        'CNAM_MEDICAMENT', 'CNAM_DENTAIRE', 'ASSURANCE_COMPLEMENTAIRE'))
);

INSERT INTO taux (code, taux, description) VALUES
    ('CNAM_CONSULTATION',         0.7000, 'Prise en charge CNAM des consultations (fictif)'),
    ('CNAM_EXAMEN',               0.8000, 'Prise en charge CNAM des examens de biologie et d''imagerie (fictif)'),
    ('CNAM_HOSPITALISATION',      0.9000, 'Prise en charge CNAM de l''hospitalisation (fictif)'),
    ('CNAM_MEDICAMENT',           0.6500, 'Prise en charge CNAM des medicaments (fictif)'),
    ('CNAM_DENTAIRE',             0.5000, 'Prise en charge CNAM des soins dentaires (fictif)'),
    ('ASSURANCE_COMPLEMENTAIRE',  0.5000, 'Part du ticket moderateur prise en charge par l''assurance complementaire (fictif)');

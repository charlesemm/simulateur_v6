-- V1 : point de depart de la base.
-- Les tables metier arrivent dans les scripts suivants (V2 referentiel, V3 catalogues, V4 facture, ...).
-- Regle : un script deja applique ne se modifie jamais ; on ajoute le suivant.

COMMENT ON SCHEMA public IS 'Simulateur de flux CNAM - donnees synthetiques uniquement';

CREATE TABLE info_application (
    cle    VARCHAR(100) PRIMARY KEY,
    valeur VARCHAR(500) NOT NULL
);

INSERT INTO info_application (cle, valeur) VALUES ('version_schema', '1');

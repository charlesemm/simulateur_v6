-- V13 : trace des telechargements d'export. Une ligne par telechargement servi.
-- AUCUNE table de lignes exportees : l'export interroge les tables metier (assure, facture,
-- facture_ligne, evenement). Pas d'index ici : ils vont dans un script separe.
-- L'empreinte est le SHA-256 des octets reellement envoyes : deux telechargements du meme run
-- dans le meme format doivent donner la meme empreinte (preuve de rejouabilite).

CREATE TABLE export_fichier (
    id             UUID        PRIMARY KEY,
    run_id         UUID        NOT NULL REFERENCES simulation_run (id) ON DELETE CASCADE,
    format         VARCHAR(10) NOT NULL,
    date_creation  TIMESTAMPTZ NOT NULL DEFAULT now(),
    nombre_lignes  BIGINT      NOT NULL,
    empreinte      CHAR(64)    NOT NULL,
    CONSTRAINT ck_export_fichier_format CHECK (format IN ('XLSX', 'CSV', 'JSON', 'CORRIGE')),
    CONSTRAINT ck_export_fichier_lignes CHECK (nombre_lignes >= 0),
    CONSTRAINT ck_export_fichier_empreinte CHECK (empreinte ~ '^[0-9a-f]{64}$')
);

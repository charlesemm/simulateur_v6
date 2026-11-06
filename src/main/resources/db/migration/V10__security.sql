-- V10 : comptes de l'interface, cles d'API hachees, journal d'audit.
-- Pas d'index ici (hors cles primaires et unicites) : ils vont dans un script separe.

CREATE TABLE app_user (
    id                       UUID         PRIMARY KEY,
    nom_utilisateur          VARCHAR(100) NOT NULL UNIQUE,
    mot_de_passe_hash        VARCHAR(100) NOT NULL,          -- BCrypt, jamais le mot de passe
    role                     VARCHAR(20)  NOT NULL,
    actif                    BOOLEAN      NOT NULL DEFAULT TRUE,
    mot_de_passe_a_changer   BOOLEAN      NOT NULL DEFAULT TRUE,
    echecs_consecutifs       INTEGER      NOT NULL DEFAULT 0,
    verrouille_jusqua        TIMESTAMPTZ,                    -- verrouillage temporaire apres trop d'echecs
    date_creation            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    date_derniere_connexion  TIMESTAMPTZ,
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'OPERATEUR', 'OBSERVATEUR'))
);

CREATE TABLE api_key (
    id                    UUID         PRIMARY KEY,
    nom                   VARCHAR(100) NOT NULL,
    hash                  VARCHAR(64)  NOT NULL UNIQUE,      -- SHA-256 (hexa) de la cle : la cle n'est jamais conservee
    actif                 BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    derniere_utilisation  TIMESTAMPTZ
);

CREATE TABLE audit_log (
    id         UUID         PRIMARY KEY,
    date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acteur     VARCHAR(100) NOT NULL,                        -- qui (utilisateur, ou "api")
    action     VARCHAR(60)  NOT NULL,                        -- quoi (RUN_LANCE, CHAOS_DECLENCHE...)
    cible      VARCHAR(200),                                 -- sur quoi (run, utilisateur...)
    details    TEXT                                          -- jamais de mot de passe ni de cle
);

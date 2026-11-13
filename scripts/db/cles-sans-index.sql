-- Cles etrangeres sans index couvrant. Lecture seule.
--
-- Une cle est couverte quand un index commence par ses colonnes (dans n'importe quel ordre entre
-- elles). Sans index, supprimer une ligne parente fait parcourir toute la table enfant, une fois par
-- ligne supprimee. Doit ne renvoyer AUCUNE ligne depuis V21 (prompt 20) ; IndexEtContraintesIntegrationTest le
-- verifie a chaque build.

SELECT c.conrelid::regclass                         AS table_enfant,
       c.conname                                    AS contrainte,
       string_agg(a.attname, ', ' ORDER BY k.rang)  AS colonnes,
       c.confrelid::regclass                        AS table_parente
FROM pg_constraint c
CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, rang)
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
WHERE c.contype = 'f'
  AND c.connamespace = 'public'::regnamespace
  AND NOT EXISTS (
        SELECT 1
        FROM pg_index i
        WHERE i.indrelid = c.conrelid
          AND (i.indkey::int2[])[0:cardinality(c.conkey) - 1] @> c.conkey
          AND (i.indkey::int2[])[0:cardinality(c.conkey) - 1] <@ c.conkey)
GROUP BY c.conrelid, c.conname, c.confrelid
ORDER BY 1, 2;

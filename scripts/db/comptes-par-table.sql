-- Nombre de lignes de chaque table, sous son nom de V12. Lecture seule.
-- A lancer avant ET apres la montee, puis comparer les deux sorties (docs/05-deploiement.md) :
-- chaque ligne d'avant doit se retrouver a l'identique apres. Les tables creees par V13 a V20
-- n'apparaissent qu'apres, c'est normal.
-- Le nom est ramene a sa forme de V12 (minuscules, sans le prefixe TB_ECHO_ pose par V17) pour
-- que les deux sorties se comparent ligne a ligne. Les tables de valeurs (TB_TV_*, TB_REF_*)
-- gardent leur nom en minuscules.
-- Teste par MonteeV12IntegrationTest.

SELECT regexp_replace(lower(table_name), '^tb_echo_', '') AS table_v12,
       (xpath('/row/n/text()',
              query_to_xml(format('SELECT count(*) AS n FROM %I', table_name), false, true, '')))[1]::text::bigint
           AS lignes
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
ORDER BY 1;

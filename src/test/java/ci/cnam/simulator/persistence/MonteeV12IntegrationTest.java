package ci.cnam.simulator.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Repetition de la montee du serveur : une base a V12 qui porte deja des simulations, migree jusqu'a la
 * derniere version. V13 a V20 n'avaient ete jouees que sur une base vide ; V16 (contraintes des anomalies),
 * V17 (renommage en TB_ECHO_*) et V19 (villes et specialites en cles etrangeres) touchent des donnees.
 *
 * <p>Pas de contexte Spring : l'application migrerait d'office jusqu'a la derniere version et remplirait le
 * referentiel. Flyway est pilote a la main, avec target=12, puis sans cible.
 */
@Testcontainers
class MonteeV12IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    /** Les 23 tables renommees par V17. Les autres gardent leur nom. */
    private static final Set<String> RENOMMEES_PAR_V17 = Set.of(
            "acte", "analyse_attendue", "anomaly_injected", "assure", "ayant_droit", "centre_sante", "chaos_action",
            "checkpoint_evenement", "echange", "evenement", "examen", "export_fichier", "facture",
            "facture_historique", "facture_ligne", "medecin", "medicament", "pharmacie", "planification_analyse",
            "quality_detection", "quality_report", "simulation_run", "taux");

    private static final List<String> ONZE_TYPES_D_ORIGINE = List.of(
            "CHAMP_VIDE", "FAUTE_ORTHOGRAPHE", "ESPACES_PARASITES", "CASSE_INCOHERENTE", "CARACTERES_SPECIAUX",
            "FORMAT_DATE_INVALIDE", "FORMAT_NUMERIQUE_INVALIDE", "FORMAT_IDENTIFIANT_INVALIDE", "VALEUR_NEGATIVE",
            "VALEUR_HORS_LISTE", "DOUBLON_EXACT");

    private static Map<String, Long> lignesAvant;
    private static Map<String, Long> lignesApres;
    private static List<List<String>> controleAvant;
    private static List<List<String>> comptesScriptAvant;
    private static List<List<String>> comptesScriptApres;
    private static MigrateResult montee;

    @BeforeAll
    static void monterUneBaseV12RemplieEtLaMigrer() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("12").load().migrate();
        try (Connection c = connexion(); Statement st = c.createStatement()) {
            st.execute(ressource("/db/montee/jeu-v12.sql"));
        }
        lignesAvant = compterLignesParTable();
        controleAvant = lignes(Files.readString(Path.of("scripts/db/controle-avant-montee.sql")));
        comptesScriptAvant = lignes(Files.readString(Path.of("scripts/db/comptes-par-table.sql")));

        montee = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        // Compte pris tout de suite : un test plus bas ajoute une anomalie.
        lignesApres = compterLignesParTable();
        comptesScriptApres = lignes(Files.readString(Path.of("scripts/db/comptes-par-table.sql")));
    }

    /** La comparaison de la procedure : toute ligne de la sortie d'avant se retrouve dans celle d'apres. */
    @Test
    void leScriptDeComptesDeLaProcedureDonneLaMemeSortieAvantEtApres() {
        assertThat(comptesScriptAvant).hasSize(24);
        assertThat(comptesScriptApres).containsAll(comptesScriptAvant);
        assertThat(comptesScriptAvant).contains(List.of("anomaly_injected", "12"), List.of("evenement", "13"));
    }

    @Test
    void leJeuRemplitLesVingtQuatreTablesDeV12() {
        assertThat(lignesAvant).hasSize(24);
        assertThat(lignesAvant).allSatisfy((table, n) -> assertThat(n).as(table).isPositive());
    }

    @Test
    void laMonteeVaJusquaLaDerniereVersionSansEchec() throws SQLException {
        assertThat(montee.success).isTrue();
        assertThat(montee.initialSchemaVersion).isEqualTo("12");
        assertThat(montee.targetSchemaVersion).isEqualTo("20");
        assertThat(montee.migrationsExecuted).isEqualTo(8);
        assertThat(valeur("select count(*) from flyway_schema_history where not success")).isZero();
    }

    @Test
    void chaqueTableGardeSonNombreDeLignesSousSonNouveauNom() {
        Map<String, Long> apres = lignesApres;
        for (Map.Entry<String, Long> avant : lignesAvant.entrySet()) {
            String nouveauNom = RENOMMEES_PAR_V17.contains(avant.getKey())
                    ? "TB_ECHO_" + avant.getKey().toUpperCase()
                    : avant.getKey();
            assertThat(apres).as("table %s devenue %s", avant.getKey(), nouveauNom).containsKey(nouveauNom);
            assertThat(apres.get(nouveauNom)).as(nouveauNom).isEqualTo(avant.getValue());
        }
        // Plus aucune table sous l'ancien nom.
        assertThat(apres.keySet()).doesNotContainAnyElementsOf(RENOMMEES_PAR_V17);
    }

    @Test
    void aucuneCleEtrangereOrphelineNiNonValidee() throws SQLException {
        assertThat(valeur("select count(*) from pg_constraint where contype = 'f' and not convalidated")).isZero();
        List<List<String>> cles = lignes("""
                select format('select count(*) from %s e where e.%I is not null and not exists '
                              '(select 1 from %s p where p.%I = e.%I)',
                              c.conrelid::regclass, a.attname, c.confrelid::regclass, pa.attname, a.attname),
                       c.conname
                from pg_constraint c
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
                join pg_attribute pa on pa.attrelid = c.confrelid and pa.attnum = c.confkey[1]
                where c.contype = 'f' and c.connamespace = 'public'::regnamespace""");
        assertThat(cles).hasSizeGreaterThan(20);
        for (List<String> cle : cles) {
            assertThat(valeur(cle.get(0))).as("orphelins de %s", cle.get(1)).isZero();
        }
        // Les trois liens sans cle etrangere.
        assertThat(valeur("""
                select count(*) from "TB_ECHO_FACTURE" f where f.beneficiaire_id not in
                  (select id from "TB_ECHO_ASSURE" union all select id from "TB_ECHO_AYANT_DROIT")""")).isZero();
        assertThat(valeur("""
                select count(*) from "TB_ECHO_FACTURE_LIGNE" l join "TB_ECHO_FACTURE" f on f.id = l.facture_id
                where l.run_id <> f.run_id""")).isZero();
        assertThat(valeur("""
                select count(*) from "TB_ECHO_FACTURE_HISTORIQUE" h join "TB_ECHO_FACTURE" f on f.id = h.facture_id
                where h.run_id <> f.run_id""")).isZero();
    }

    @Test
    void lesContraintesDeV16AcceptentLesAnciennesAnomalies() throws SQLException {
        assertThat(lignes("select distinct type from \"TB_ECHO_ANOMALY_INJECTED\" order by 1"))
                .extracting(l -> l.get(0)).containsExactlyInAnyOrderElementsOf(ONZE_TYPES_D_ORIGINE);
        assertThat(lignes("""
                select doublon_de::text from "TB_ECHO_ANOMALY_INJECTED" where type = 'DOUBLON_EXACT'"""))
                .containsExactly(List.of("bbbbbbbb-0000-0000-0000-000000000004"));
        // Reecrire chaque ligne force PostgreSQL a reverifier les CHECK recrees par V16 sur les anciennes lignes.
        assertThat(valeur("""
                with t as (update "TB_ECHO_ANOMALY_INJECTED" set type = type, doublon_de = doublon_de returning 1)
                select count(*) from t""")).isEqualTo(12);
        // Et la table, renommee, accepte les nouveaux types : l'identite continue de numeroter.
        assertThatCode(() -> executer("""
                insert into "TB_ECHO_ANOMALY_INJECTED" (run_id, event_id, facture_id, type, champ, doublon_de)
                values ('11111111-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000006',
                        '88888888-0000-0000-0000-000000000001', 'DOUBLON_APPROXIMATIF', null,
                        'bbbbbbbb-0000-0000-0000-000000000005')""")).doesNotThrowAnyException();
        assertThat(valeur("select max(id) from \"TB_ECHO_ANOMALY_INJECTED\"")).isEqualTo(13);
    }

    @Test
    void lesVillesSontRattacheesAUnDepartementEtLesInconnuesAAbidjan() throws SQLException {
        assertThat(lignes("""
                select code, departement_code, type_etablissement_code from "TB_ECHO_CENTRE_SANTE" order by code"""))
                .containsExactly(
                        List.of("CS-T01", "D109", "HG"),     // Abidjan
                        List.of("CS-T02", "D030", "CLI"),    // Bouake
                        List.of("CS-T03", "D109", "CSU"),    // "Yamousoukro", mal orthographiee : Abidjan par defaut
                        List.of("CS-T04", "D109", "CHR"),    // "Tombouctou", absente : Abidjan par defaut
                        List.of("CS-T05", departement("Korhogo"), "CSU"));
        assertThat(lignes("select code, departement_code from \"TB_ECHO_PHARMACIE\" order by code"))
                .containsExactly(
                        List.of("PH-T01", departement("Daloa")),
                        List.of("PH-T02", "D109"),           // "korhogo", casse differente : Abidjan par defaut
                        List.of("PH-T03", departement("Man")));
    }

    @Test
    void lesSpecialitesSontRattacheesAuCatalogueEtLesInconnuesAMedecineGenerale() throws SQLException {
        assertThat(lignes("select code, specialite_medicale_code from \"TB_ECHO_MEDECIN\" order by code"))
                .containsExactly(
                        List.of("MD-T01", "MG"),
                        List.of("MD-T02", "RAD"),            // "Radiologie" : conversion explicite de V19
                        List.of("MD-T03", "CAR"),
                        List.of("MD-T04", "DER"),            // libelle exact du catalogue
                        List.of("MD-T05", "MG"));            // inconnue : Medecine generale par defaut
    }

    /** Les quinze villes que le generateur de la v1.1.0 (V12) a pu ecrire sont toutes des departements. */
    @Test
    void lesQuinzeVillesDuGenerateurV12SontToutesDesDepartements() throws SQLException {
        assertThat(valeur("""
                select count(distinct departement_libelle) from "TB_TV_LOCALISATION_DEPARTEMENTS"
                where departement_libelle in ('Abidjan', 'Bouake', 'Daloa', 'Yamoussoukro', 'San-Pedro', 'Korhogo',
                  'Man', 'Abengourou', 'Gagnoa', 'Divo', 'Odienne', 'Bondoukou', 'Ferkessedougou', 'Sassandra',
                  'Dimbokro')""")).isEqualTo(15);
    }

    /** Le script de la procedure annonce, AVANT la montee, exactement les valeurs que V19 rattacherait par defaut. */
    @Test
    void leControleAvantMonteeSignaleLesValeursRattacheesParDefaut() {
        assertThat(controleAvant).containsExactly(
                List.of("centre_sante", "CS-T03", "Yamousoukro", "D109 (Abidjan)"),
                List.of("centre_sante", "CS-T04", "Tombouctou", "D109 (Abidjan)"),
                List.of("medecin", "MD-T05", "Specialite inconnue", "MG (Medecine generale)"),
                List.of("pharmacie", "PH-T02", "korhogo", "D109 (Abidjan)"));
    }

    @Test
    void factureEtAssuresPortentLeursCodesOfficiels() throws SQLException {
        assertThat(lignes("select numero_facture, type_facture_code, statut from \"TB_ECHO_FACTURE\" order by 1"))
                .containsExactly(
                        List.of("FAC-2026-000001", "ER-01-GPR", "FINALISEE"),
                        List.of("FAC-2026-000002", "ER-02-GPR", "REFUSEE"),
                        List.of("FAC-2026-000003", "ER-03-GPR", "EN_COURS"),
                        List.of("FAC-2026-000004", "DENTAIRE", "ABANDONNEE"),
                        List.of("FAC-2026-000005", "ER-01-GPR", "OUVERTE"));
        assertThat(lignes("select distinct regime_code, genre from \"TB_ECHO_ASSURE\" order by 2"))
                .containsExactly(List.of("RGB", "F"), List.of("RGB", "M"));
        // Les montants ne bougent pas.
        assertThat(valeur("select sum(montant_total)::bigint from \"TB_ECHO_FACTURE\"")).isEqualTo(13000);
    }

    // --- outils -------------------------------------------------------------------------------------

    private static Connection connexion() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static String ressource(String chemin) throws IOException {
        try (InputStream in = MonteeV12IntegrationTest.class.getResourceAsStream(chemin)) {
            assertThat(in).as(chemin).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Nombre de lignes de chaque table du schema public, hors historique de Flyway. */
    private static Map<String, Long> compterLignesParTable() throws SQLException {
        Map<String, Long> comptes = new LinkedHashMap<>();
        for (List<String> table : lignes("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'
                order by 1""")) {
            String nom = table.get(0);
            comptes.put(nom, valeur("select count(*) from \"" + nom + "\""));
        }
        return comptes;
    }

    private static List<List<String>> lignes(String sql) throws SQLException {
        try (Connection c = connexion(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<List<String>> resultat = new ArrayList<>();
            int colonnes = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> ligne = new ArrayList<>();
                for (int i = 1; i <= colonnes; i++) {
                    ligne.add(rs.getString(i));
                }
                resultat.add(ligne);
            }
            return resultat;
        }
    }

    private static long valeur(String sql) throws SQLException {
        return Long.parseLong(lignes(sql).get(0).get(0));
    }

    private static void executer(String sql) throws SQLException {
        try (Connection c = connexion(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static String departement(String libelle) throws SQLException {
        return lignes("select departement_code from \"TB_TV_LOCALISATION_DEPARTEMENTS\" where departement_libelle = '"
                + libelle + "'").get(0).get(0);
    }
}

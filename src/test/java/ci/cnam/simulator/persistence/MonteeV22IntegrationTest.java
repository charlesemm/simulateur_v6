package ci.cnam.simulator.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V22 sur une base REMPLIE qui porte des lignes hors contrainte : la migration s'arrete en disant
 * lesquelles, ne modifie rien, et passe une fois ces lignes traitees (procedure 9 ter de
 * docs/05-deploiement.md). Le cas sain (une base remplie par l'application) est couvert par
 * MonteeV12IntegrationTest, qui monte jusqu'a la derniere version.
 */
@Testcontainers
class MonteeV22IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final String RUN = "11111111-0000-0000-0000-000000000001";

    @Test
    void v22RefuseUneBaseHorsContrainteSansRienModifierPuisPasseUneFoisLesLignesTraitees() throws Exception {
        flyway("21").migrate();
        executer("""
                insert into scenario (id, nom, contenu_yaml)
                values ('f0f0f0f0-0000-0000-0000-000000000001', 'montee-v22', 'nom: montee-v22');
                insert into "TB_ECHO_SIMULATION_RUN" (id, scenario_id, seed, statut, debut_simule, parametres)
                values ('%1$s', 'f0f0f0f0-0000-0000-0000-000000000001', 7, 'ARRETE', now(), '{}');
                insert into "TB_ECHO_EVENEMENT" (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel,
                                                 payload, statut) values
                 ('bbbbbbbb-0000-0000-0000-000000000001', '%1$s', 'prestations', 'PATIENT_ARRIVE', now(), now(), '{}', 'ECRIT'),
                 ('bbbbbbbb-0000-0000-0000-000000000002', '%1$s', 'prestations', 'TEST',           now(), now(), '{}', 'ECRIT'),
                 ('bbbbbbbb-0000-0000-0000-000000000003', '%1$s', 'Prestations', 'FACTURE_OUVERTE', now(), now(), '{}', 'ECRIT'),
                 ('bbbbbbbb-0000-0000-0000-000000000004', '%1$s', 'prestations', 'FACTURE_OUVERTE', now(), now(), '{}', 'PERDU');
                """.formatted(RUN));

        // 1. Le controle de la procedure liste les trois lignes fautives.
        String controle = Files.readString(Path.of("scripts/db/controle-avant-v22.sql"));
        assertThat(lignes(controle)).containsExactly(
                List.of("TB_ECHO_EVENEMENT", "domaine", "Prestations", "1", "1"),
                List.of("TB_ECHO_EVENEMENT", "statut", "PERDU", "1", "1"),
                List.of("TB_ECHO_EVENEMENT", "type", "TEST", "1", "1"));

        // 2. La migration s'arrete et dit pourquoi.
        assertThatThrownBy(() -> flyway(null).migrate())
                .hasStackTraceContaining("V22 refusee")
                .hasStackTraceContaining("1 statut, 1 type, 1 domaine");

        // 3. Rien n'a bouge : base a V21, les quatre evenements la, aucune contrainte posee.
        assertThat(lignes("select max(version::int)::text from flyway_schema_history where success"))
                .containsExactly(List.of("21"));
        assertThat(lignes("select count(*)::text from \"TB_ECHO_EVENEMENT\"")).containsExactly(List.of("4"));
        assertThat(lignes("select count(*)::text from pg_constraint where conname like 'ck_evenement_%'"))
                .containsExactly(List.of("0"));

        // 4. Lignes traitees (ici : corrigees ou supprimees, selon la procedure), la montee passe.
        executer("""
                update "TB_ECHO_EVENEMENT" set domaine = 'prestations' where domaine = 'Prestations';
                update "TB_ECHO_EVENEMENT" set statut = 'ECHOUE' where statut = 'PERDU';
                delete from "TB_ECHO_EVENEMENT" where type = 'TEST';
                """);
        assertThat(lignes(controle)).isEmpty();

        MigrateResult montee = flyway(null).migrate();
        assertThat(montee.success).isTrue();
        assertThat(montee.targetSchemaVersion).isEqualTo("22");
        assertThat(lignes("select count(*)::text from pg_constraint where conname like 'ck_evenement_%'"))
                .containsExactly(List.of("3"));
    }

    private static Flyway flyway(String cible) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (cible != null) {
            config.target(cible);
        }
        return config.load();
    }

    private static Connection connexion() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void executer(String sql) throws SQLException {
        try (Connection c = connexion(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
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
}

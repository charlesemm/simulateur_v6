package ci.cnam.simulator.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Mesure avant / apres les index (V21) et les contraintes (V22), sur un VRAI run d'au moins
 * 100 000 evenements : le moteur genere les parcours, le DatabaseSink ecrit evenements, factures,
 * lignes et historique par lots. Trois chiffres : debit d'ecriture, lecture paginee des evenements
 * du run (celle de l'explorateur : count + page), suppression du run (cascade).
 *
 * <p>Ce n'est pas un test de regression : il ne tourne pas dans {@code clean verify}. La base est
 * montee jusqu'a {@code mesure.cible} : 20 pour l'avant, {@code latest} pour l'apres.
 *
 * <pre>
 * .\mvnw.cmd test -Dtest=IndexMesureTest -Dmesure.index=true -Dmesure.cible=20
 * .\mvnw.cmd test -Dtest=IndexMesureTest -Dmesure.index=true -Dmesure.cible=latest
 * </pre>
 */
@SpringBootTest(properties = "spring.flyway.target=${mesure.cible:latest}")
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "mesure.index", matches = "true")
class IndexMesureTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final long EVENEMENTS = 100_000;
    private static final int REPETITIONS = 7;

    /** Debit volontairement au-dessus de ce que l'ecriture absorbe : la contre-pression du sink fixe le rythme. */
    private static final String YAML = """
            nom: "mesure-index"
            seed: 2026
            accelerationTemps: 100
            debutSimule: 2026-03-10T08:00:00
            domaines:
              prestations:
                debit: 2000/s
                parcours: { examens: 60%, hospitalisation: 10%, prescriptionDirecte: 30% }
            """;

    @Autowired RunManager manager;
    @Autowired ScenarioLoader loader;
    @Autowired EventQueryService evenements;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;

    @Test
    @DisplayName("MESURE : ecriture, lecture paginee et suppression d'un run de 100 000 evenements")
    void mesurer() throws Exception {
        String version = jdbc.queryForObject(
                "select version from flyway_schema_history where success and version is not null "
                        + "order by installed_rank desc limit 1", String.class);

        RunInfo run = manager.creer(loader.lire(YAML));
        long debut = System.nanoTime();
        manager.demarrer(run.id());
        while (manager.info(run.id()).evenementsEmis() < EVENEMENTS) {
            Thread.sleep(50);
        }
        manager.arreter(run.id());   // vide le sink : tout est en base au retour
        double secondes = (System.nanoTime() - debut) / 1e9;
        long ecrits = compter("TB_ECHO_EVENEMENT", run.id());
        long factures = compter("TB_ECHO_FACTURE", run.id());
        long lignes = compter("TB_ECHO_FACTURE_LIGNE", run.id());
        long historique = compter("TB_ECHO_FACTURE_HISTORIQUE", run.id());
        assertThat(ecrits).isGreaterThanOrEqualTo(EVENEMENTS);

        jdbc.execute("analyze");
        UUID id = run.id();
        double page0 = mediane(() -> evenements.rechercher(id, null, null, null, null, 0, 50));
        double page1000 = mediane(() -> evenements.rechercher(id, null, null, null, null, 1000, 50));
        double parType = mediane(() -> evenements.rechercher(id, null, null, "FACTURE_FINALISEE", null, 0, 50));
        double unEvenement = mediane(() -> evenements.rechercher(id, premierEvenement(id), null, null, null, 0, 50));

        if (Boolean.getBoolean("mesure.plans")) {
            plan("explain (analyze, buffers) select count(*) from \"TB_ECHO_EVENEMENT\" where run_id = '" + id + "'");
            plan("explain (analyze, buffers) select * from \"TB_ECHO_EVENEMENT\" where run_id = '" + id
                    + "' order by timestamp_reel, event_id limit 50 offset 0");
            plan("explain (analyze, buffers) select * from \"TB_ECHO_EVENEMENT\" where run_id = '" + id
                    + "' order by timestamp_reel, event_id limit 50 offset 50000");
            new TransactionTemplate(tx).executeWithoutResult(statut -> {
                plan("explain (analyze, buffers) delete from \"TB_ECHO_SIMULATION_RUN\" where id = '" + id + "'");
                statut.setRollbackOnly();   // EXPLAIN ANALYZE execute vraiment le delete
            });
        }

        long t = System.nanoTime();
        int supprime = jdbc.update("delete from \"TB_ECHO_SIMULATION_RUN\" where id = ?", id);
        double suppression = (System.nanoTime() - t) / 1e6;
        assertThat(supprime).isEqualTo(1);
        assertThat(compter("TB_ECHO_EVENEMENT", id)).isZero();

        System.out.printf("""

                === MESURE INDEX (base a V%s) ===
                ecriture     : %d evenements (+ %d factures, %d lignes, %d historiques) en %.1f s = %.0f evenements/s
                lecture      : page 0 %.1f ms | page 1000 %.1f ms | filtre type %.1f ms | un evenement %.1f ms (medianes sur %d)
                suppression  : %.0f ms
                %n""", version, ecrits, factures, lignes, historique, secondes, ecrits / secondes,
                page0, page1000, parType, unEvenement, REPETITIONS, suppression);
    }

    private void plan(String explain) {
        System.out.println("\n--- " + explain);
        jdbc.queryForList(explain, String.class).forEach(System.out::println);
    }

    private UUID premierEvenement(UUID run) {
        return jdbc.queryForObject("select event_id from \"TB_ECHO_EVENEMENT\" where run_id = ? limit 1", UUID.class, run);
    }

    private long compter(String table, UUID run) {
        Long n = jdbc.queryForObject("select count(*) from \"" + table + "\" where run_id = ?", Long.class, run);
        return n == null ? 0 : n;
    }

    private static double mediane(Supplier<PageEvenements> lecture) {
        lecture.get();   // chauffe
        List<Double> durees = new ArrayList<>();
        for (int i = 0; i < REPETITIONS; i++) {
            long t = System.nanoTime();
            PageEvenements page = lecture.get();
            durees.add((System.nanoTime() - t) / 1e6);
            assertThat(page.total()).isPositive();
        }
        Collections.sort(durees);
        return durees.get(REPETITIONS / 2);
    }
}

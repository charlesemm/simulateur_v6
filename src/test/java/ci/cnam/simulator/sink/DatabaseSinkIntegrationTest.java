package ci.cnam.simulator.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.monitoring.MetricsService;
import ci.cnam.simulator.persistence.repository.RunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Ecriture par lots sur un vrai PostgreSQL : volume, atomicite, reprise, contre-pression. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DatabaseSinkIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager tx;
    @Autowired
    ObjectMapper json;
    @Autowired
    RunRepository runs;
    @Autowired
    ScenarioLoader loader;

    private UUID nouveauRun() {
        UUID id = UUID.randomUUID();
        runs.creer(id, loader.lire("""
                nom: "sink-%s"
                seed: 1
                domaines:
                  prestations: { debit: 1, parcours: { examens: 60%%, hospitalisation: 10%%, prescriptionDirecte: 30%% } }
                """.formatted(id)));
        return id;
    }

    private static SimEvent evenement(UUID runId, int i) {
        return new SimEvent(UUID.nameUUIDFromBytes((runId + ":" + i).getBytes()), runId, "prestations", "TEST",
                LocalDateTime.of(2026, 3, 10, 8, 0).plusSeconds(i), Map.of("n", i), null);
    }

    private DatabaseSink sink(int lot, int file, long delaiMs, int tentatives) {
        return new DatabaseSink(jdbc, tx, json, Clock.systemUTC(), new MetricsService(new SimpleMeterRegistry()),
                new SimulateurProperties.Ecriture(lot, file, delaiMs, tentatives, 30_000));
    }

    private long nombre(UUID runId) {
        return jdbc.queryForObject("select count(*) from evenement where run_id = ?", Long.class, runId);
    }

    @Test
    void ecrit100000EvenementsEtMesureLeDebit() {
        UUID run = nouveauRun();
        try (DatabaseSink sink = sink(1000, 10_000, 200, 3)) {
            sink.demarrer();
            long debut = System.nanoTime();
            for (int i = 0; i < 100_000; i++) {
                sink.emettre(evenement(run, i));
            }
            sink.flush();
            double secondes = (System.nanoTime() - debut) / 1e9;
            System.out.printf("DEBIT-ECRITURE : 100000 evenements en %.2f s = %.0f evenements/s%n", secondes,
                    100_000 / secondes);

            assertThat(nombre(run)).isEqualTo(100_000);
            assertThat(sink.checkpoint(run)).hasValueSatisfying(c -> {
                assertThat(c.compteur()).isEqualTo(100_000);
                assertThat(c.dernierEventId()).isEqualTo(evenement(run, 99_999).eventId());
            });
        }
    }

    @Test
    void uneExceptionAuMilieuDUnLotAnnuleLeLotEntier() {
        UUID run = nouveauRun();
        List<SimEvent> lot = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            // L'evenement 250 pointe vers un run inexistant : la base refuse (cle etrangere) en plein lot.
            lot.add(i == 250 ? evenement(UUID.randomUUID(), i) : evenement(run, i));
        }
        DatabaseSink sink = sink(500, 1000, 100, 1);

        assertThatThrownBy(() -> sink.ecrireLot(lot)).hasMessageContaining("evenement_run_id_fkey");

        assertThat(nombre(run)).isZero();   // aucune ligne partielle
        assertThat(sink.checkpoint(run)).isEmpty();   // et le checkpoint n'a pas bouge
    }

    @Test
    void rejouerUnLotNeCreeAucunDoublonNiNeFaitBougerLeCompteur() {
        UUID run = nouveauRun();
        List<SimEvent> lot = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            lot.add(evenement(run, i));
        }
        DatabaseSink sink = sink(500, 1000, 100, 1);

        sink.ecrireLot(lot);
        sink.ecrireLot(lot);

        assertThat(nombre(run)).isEqualTo(300);
        assertThat(sink.checkpoint(run)).hasValueSatisfying(c -> assertThat(c.compteur()).isEqualTo(300));
    }

    @Test
    void apresUnArretBrutalLaRepriseDonneLeNombreExactSansDoublon() {
        UUID run = nouveauRun();
        int total = 5_000;

        // Premier processus : la liaison tombe apres 4 lots. Le reste (file, lot en cours) est perdu.
        AtomicInteger lots = new AtomicInteger();
        DatabaseSink avantPanne = new DatabaseSink(jdbc, tx, json, Clock.systemUTC(),
                new MetricsService(new SimpleMeterRegistry()),
                new SimulateurProperties.Ecriture(500, 2_000, 50, 1, 5_000)) {
            @Override
            public void ecrireLot(List<SimEvent> lot, List<EcritureAnnexe> annexes) {
                if (lots.incrementAndGet() > 4) {
                    throw new IllegalStateException("liaison coupee");
                }
                super.ecrireLot(lot, annexes);
            }
        };
        avantPanne.demarrer();
        try {
            for (int i = 0; i < total; i++) {
                avantPanne.emettre(evenement(run, i));
            }
            avantPanne.flush();
        } catch (IllegalStateException attendu) {
            // le sink est passe en echec : c'est la panne
        } finally {
            avantPanne.close();
        }

        long ecrits = nombre(run);
        assertThat(ecrits).isPositive().isLessThan(total);   // des lots valides, mais pas tout
        DatabaseSink apresRedemarrage = sink(500, 2_000, 50, 1);
        long compteur = apresRedemarrage.checkpoint(run).orElseThrow().compteur();
        assertThat(compteur).isEqualTo(ecrits);   // le checkpoint colle aux lignes reellement ecrites

        // Second processus : reprise apres le checkpoint, avec 100 evenements de recouvrement (au moins une fois).
        apresRedemarrage.demarrer();
        try {
            for (int i = (int) compteur - 100; i < total; i++) {
                apresRedemarrage.emettre(evenement(run, i));
            }
            apresRedemarrage.flush();
        } finally {
            apresRedemarrage.close();
        }

        assertThat(nombre(run)).isEqualTo(total);
        assertThat(jdbc.queryForObject("select count(distinct event_id) from evenement where run_id = ?", Long.class,
                run)).isEqualTo(total);
        assertThat(apresRedemarrage.checkpoint(run).orElseThrow().compteur()).isEqualTo(total);
    }

    @Test
    void uneFileLenteRalentitLeGenerateurSansDepasserLaCapacite() throws Exception {
        UUID run = nouveauRun();
        AtomicLong tailleMax = new AtomicLong();
        DatabaseSink lent = new DatabaseSink(jdbc, tx, json, Clock.systemUTC(),
                new MetricsService(new SimpleMeterRegistry()),
                new SimulateurProperties.Ecriture(50, 100, 20, 1, 30_000)) {
            @Override
            public void ecrireLot(List<SimEvent> lot, List<EcritureAnnexe> annexes) {
                try {
                    Thread.sleep(100);   // disque lent
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.ecrireLot(lot, annexes);
            }
        };
        lent.demarrer();
        try {
            long debut = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                lent.emettre(evenement(run, i));
                tailleMax.accumulateAndGet(lent.tailleFile(), Math::max);
            }
            long msEmission = (System.nanoTime() - debut) / 1_000_000;
            lent.flush();

            // 1000 evenements / lots de 50 = 20 lots de 100 ms : le generateur ne peut pas finir bien avant 1,5 s.
            assertThat(msEmission).isGreaterThan(1_200);
            assertThat(tailleMax.get()).isLessThanOrEqualTo(100);
            assertThat(nombre(run)).isEqualTo(1_000);
        } finally {
            lent.close();
        }
    }

    @Test
    void sinkEnEchecFaitEchouerLEmetteurAuLieuDePerdreEnSilence() {
        UUID run = nouveauRun();
        DatabaseSink casse = new DatabaseSink(jdbc, tx, json, Clock.systemUTC(),
                new MetricsService(new SimpleMeterRegistry()),
                new SimulateurProperties.Ecriture(10, 100, 20, 2, 5_000)) {
            @Override
            public void ecrireLot(List<SimEvent> lot, List<EcritureAnnexe> annexes) {
                throw new IllegalStateException("base indisponible");
            }
        };
        casse.demarrer();
        try {
            casse.emettre(evenement(run, 0));
            assertThatThrownBy(casse::flush).hasMessageContaining("base indisponible");
            assertThatThrownBy(() -> casse.emettre(evenement(run, 1))).hasMessageContaining("base indisponible");
        } finally {
            casse.close();
        }
    }
}

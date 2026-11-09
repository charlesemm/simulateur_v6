package ci.cnam.simulator.domain.prestations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.persistence.repository.RunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Un run complet sur PostgreSQL : scenario YAML, moteur, generateur de prestations, vraies factures. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PrestationsRunIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final String YAML = """
            nom: "IT - prestations"
            seed: 42
            accelerationTemps: 100
            debutSimule: 2026-03-10T08:00:00
            domaines:
              prestations:
                debit: 5/s
                parcours: { examens: 60%, hospitalisation: 10%, prescriptionDirecte: 30% }
            """;

    @Autowired
    RunManager manager;
    @Autowired
    ScenarioLoader loader;
    @Autowired
    RunRepository runs;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void unRunProduitDesParcoursEnBaseSArreteEtSeSupprimeEnCascade() throws Exception {
        Scenario scenario = loader.lire(YAML);
        RunInfo run = manager.creer(scenario);
        assertThat(runs.statut(run.id())).isEqualTo("CREE");

        manager.demarrer(run.id());
        assertThat(runs.statut(run.id())).isEqualTo("EN_COURS");
        Thread.sleep(2500);
        manager.pause(run.id());
        assertThat(runs.statut(run.id())).isEqualTo("EN_PAUSE");
        RunInfo enPause = manager.info(run.id());
        manager.arreter(run.id());

        assertThat(runs.statut(run.id())).isEqualTo("ARRETE");
        assertThat(manager.info(run.id()).etat()).isEqualTo(EtatRun.ARRETE);
        assertThat(enPause.parcoursGeneres()).isBetween(5L, 20L);

        // arreter() a vide le sink : tout ce qui a ete emis est en base.
        List<Map<String, Object>> evenements = jdbc.queryForList(
                "select type, timestamp_simule from \"TB_ECHO_EVENEMENT\" where run_id = ? order by timestamp_simule", run.id());
        assertThat(evenements).hasSize((int) manager.info(run.id()).evenementsEmis());
        long arrivees = evenements.stream().filter(e -> e.get("type").equals("PATIENT_ARRIVE")).count();
        assertThat(arrivees).isEqualTo(manager.info(run.id()).parcoursGeneres());
        assertThat(evenements.stream().map(e -> (String) e.get("type"))).contains("FACTURE_OUVERTE", "FACTURE_FINALISEE");
        // Les dates simulees avancent a x100 depuis le debut du scenario.
        assertThat(((java.sql.Timestamp) evenements.get(0).get("timestamp_simule")).toLocalDateTime())
                .isAfterOrEqualTo(LocalDateTime.of(2026, 3, 10, 8, 0));
        assertThat(jdbc.queryForObject("select compteur from \"TB_ECHO_CHECKPOINT_EVENEMENT\" where run_id = ?", Long.class,
                run.id())).isEqualTo(evenements.size());

        Integer factures = jdbc.queryForObject("select count(*) from \"TB_ECHO_FACTURE\" where run_id = ?", Integer.class, run.id());
        assertThat(factures).isPositive();
        Map<String, Object> enregistre = jdbc.queryForMap(
                "select seed, statut, date_debut, date_fin, parametres::text as parametres from \"TB_ECHO_SIMULATION_RUN\" where id = ?",
                run.id());
        assertThat(enregistre.get("seed")).isEqualTo(42L);
        assertThat(enregistre.get("date_debut")).isNotNull();
        assertThat(enregistre.get("date_fin")).isNotNull();
        assertThat((String) enregistre.get("parametres")).contains("prestations").contains("accelerationTemps");
        assertThat(jdbc.queryForObject("select contenu_yaml from scenario where nom = 'IT - prestations'",
                String.class)).isEqualTo(YAML);

        jdbc.update("delete from \"TB_ECHO_SIMULATION_RUN\" where id = ?", run.id());
        assertThat(jdbc.queryForObject("select count(*) from \"TB_ECHO_EVENEMENT\" where run_id = ?", Integer.class, run.id()))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from \"TB_ECHO_FACTURE\" where run_id = ?", Integer.class, run.id()))
                .isZero();
    }

    @Test
    void uneFactureNePeutPasPointerVersUnRunInexistant() {
        UUID assure = jdbc.queryForObject("select id from \"TB_ECHO_ASSURE\" limit 1", UUID.class);
        UUID centre = jdbc.queryForObject("select id from \"TB_ECHO_CENTRE_SANTE\" limit 1", UUID.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into "TB_ECHO_FACTURE" (id, run_id, numero_facture, assure_id, beneficiaire_id, centre_sante_id,
                                     type_bon, statut, date_ouverture)
                values (?, ?, 'FAC-2026-999999', ?, ?, ?, 'AMBULATOIRE', 'OUVERTE', now())""",
                UUID.randomUUID(), UUID.randomUUID(), assure, assure, centre))
                .hasMessageContaining("fk_facture_run");
    }

    @Test
    void refuseUnParcoursMalReglePourLesPrestations() {
        Scenario mal = loader.lire("""
                nom: mal
                seed: 1
                domaines:
                  prestations: { debit: 5, parcours: { examens: 50%, autre: 50% } }
                """);

        assertThatThrownBy(() -> manager.creer(mal)).hasMessageContaining("domaines.prestations.parcours");
    }
}

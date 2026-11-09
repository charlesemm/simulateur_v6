package ci.cnam.simulator.domain.reference;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.seed.ReferenceSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Remplissage reel du referentiel sur PostgreSQL : volume, contraintes, idempotence, tirages. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ReferenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ReferenceProperties proprietes;
    @Autowired
    ReferenceSeeder seeder;
    @Autowired
    ReferenceService service;

    private int compter(String table) {
        return jdbc.queryForObject("select count(*) from \"TB_ECHO_" + table.toUpperCase() + "\"", Integer.class);
    }

    @Test
    void demarrageRemplitLeNombreDAssuresConfigure() {
        assertThat(compter("assure")).isEqualTo(proprietes.nbAssures()).isEqualTo(500);
        assertThat(compter("centre_sante")).isPositive();
        assertThat(compter("medecin")).isPositive();
        assertThat(compter("pharmacie")).isPositive();
        assertThat(compter("acte")).isPositive();
        assertThat(compter("examen")).isPositive();
        assertThat(compter("medicament")).isPositive();
        assertThat(compter("ayant_droit")).isPositive();
    }

    @Test
    void secondPassageNeDuppliquePas() throws Exception {
        seeder.run(new DefaultApplicationArguments());
        assertThat(compter("assure")).isEqualTo(500);
    }

    @Test
    void tiragesReproductiblesEtCoherents() {
        var a = service.assureAleatoire(new SeededRandom(3));
        assertThat(a).isEqualTo(service.assureAleatoire(new SeededRandom(3)));

        var centre = service.centreAleatoire(new SeededRandom(3));
        Medecin medecin = service.medecinAleatoire(centre, new SeededRandom(3));
        assertThat(medecin.centreSanteId()).isEqualTo(centre.id());
        assertThat(service.pharmacieAleatoire(new SeededRandom(3))).isNotNull();
        assertThat(service.medecinAleatoire(new SeededRandom(3))).isNotNull();
    }
}

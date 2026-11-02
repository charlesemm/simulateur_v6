package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.SupportRun;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.facture.FactureService;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.reference.ReferenceService;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Parcours complet sur PostgreSQL : vrai referentiel, vraie FactureService, vrais ccâblages Spring. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ParcoursIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"));

    @Autowired
    ParcoursOrchestrator orchestrateur;
    @Autowired
    ReferenceService reference;
    @Autowired
    FactureService factureService;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void unParcoursSurLaBaseSeTermineEtLaTraceEstAffichee() {
        SeededRandom random = new SeededRandom(42);
        UUID run = SupportRun.nouveauRun(jdbc);
        ParcoursOrchestrator.Resultat resultat = null;
        // Cherche un assure aux droits valides parmi le referentiel de test (500 assures).
        for (int i = 0; i < 200 && (resultat == null || resultat.facture().statut() != FactureStatut.FINALISEE); i++) {
            Assure assure = reference.assureAleatoire(random);
            CentreSante centre = reference.centreAleatoire(random);
            resultat = orchestrateur.executer(run, assure, centre, reference.medecinAleatoire(centre, random),
                    reference.pharmacieAleatoire(random), random);
        }

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.FINALISEE);
        assertThat(factureService.trouver(resultat.facture().id()).statut()).isEqualTo(FactureStatut.FINALISEE);
        assertThat(factureService.lignes(resultat.facture().id())).isNotEmpty();
        assertThat(factureService.historique(resultat.facture().id())).hasSizeGreaterThanOrEqualTo(4);

        System.out.println("=== TRACE DU PARCOURS " + resultat.facture().numeroFacture() + " ("
                + resultat.facture().typeBon() + ") ===");
        for (SimEvent e : resultat.evenements()) {
            System.out.println(e.timestampSimule() + "  " + e.type() + "  " + e.payload());
        }
    }
}

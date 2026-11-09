package ci.cnam.simulator.domain.prestations.facture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.SupportRun;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.monitoring.MetricsService;
import ci.cnam.simulator.sink.DatabaseSink;
import ci.cnam.simulator.sink.EcritureAnnexe;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Les factures sont ecrites PAR LOTS, dans la transaction des evenements : tout ou rien, rejouable sans doublon,
 * et jamais d'evenement en base avant la facture dont il parle.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FactureLotIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 1, 8, 0);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager tx;
    @Autowired
    ObjectMapper json;
    @Autowired
    FactureEcrivain ecrivain;
    @Autowired
    NumerotationFactures numerotation;

    private DatabaseSink sink() {
        return new DatabaseSink(jdbc, tx, json, Clock.systemUTC(), new MetricsService(new SimpleMeterRegistry()),
                new SimulateurProperties.Ecriture(500, 1_000, 50, 1, 5_000), List.of(ecrivain));
    }

    /** Un parcours termine : facture OUVERTE -> EN_COURS (une ligne) -> FINALISEE, comme le fait le generateur. */
    private FactureEcrite unParcours(UUID run) {
        GestionFactureMemoire gestion = new GestionFactureMemoire(numerotation);
        UUID assure = jdbc.queryForObject("select id from \"TB_ECHO_ASSURE\" limit 1", UUID.class);
        UUID centre = jdbc.queryForObject("select id from \"TB_ECHO_CENTRE_SANTE\" limit 1", UUID.class);
        Facture f = gestion.ouvrir(new NouvelleFacture(run, assure, assure, centre, TypeBon.AMBULATOIRE, T0));
        gestion.ajouterLigne(f.id(), new NouvelleLigne("C01", "Consultation", T0.plusMinutes(10), 1,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), new BigDecimal("4000.00"),
                new BigDecimal("500.00"), new BigDecimal("500.00")));
        gestion.changerStatut(f.id(), FactureStatut.EN_COURS, T0.plusMinutes(20));
        gestion.changerStatut(f.id(), FactureStatut.PRESCRITE, T0.plusMinutes(30));
        gestion.changerStatut(f.id(), FactureStatut.MEDICAMENTS_DELIVRES, T0.plusMinutes(40));
        gestion.changerStatut(f.id(), FactureStatut.FINALISEE, T0.plusMinutes(50));
        return (FactureEcrite) gestion.clore(f.id()).orElseThrow();
    }

    private static SimEvent evenement(UUID run, UUID facture, String type, int i) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("factureId", facture);
        return new SimEvent(UUID.nameUUIDFromBytes((run + ":" + facture + ":" + type + i).getBytes()), run, "prestations", type,
                T0.plusMinutes(i), payload, null);
    }

    private long compter(String table, UUID run) {
        return jdbc.queryForObject("select count(*) from \"TB_ECHO_" + table.toUpperCase() + "\" where run_id = ?", Long.class, run);
    }

    @Test
    void uneFactureEstEcriteAvecSesLignesSonHistoriqueEtSesEvenementsEnUnSeulLot() {
        UUID run = SupportRun.nouveauRun(jdbc);
        FactureEcrite ecrite = unParcours(run);
        List<SimEvent> evenements = List.of(evenement(run, ecrite.facture().id(), "FACTURE_OUVERTE", 1),
                evenement(run, ecrite.facture().id(), "FACTURE_FINALISEE", 2));

        sink().ecrireLot(evenements, List.of(ecrite));

        assertThat(compter("facture", run)).isEqualTo(1);
        assertThat(compter("facture_ligne", run)).isEqualTo(1);
        assertThat(compter("facture_historique", run)).isEqualTo(5);
        assertThat(compter("evenement", run)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select statut from \"TB_ECHO_FACTURE\" where id = ?", String.class,
                ecrite.facture().id())).isEqualTo("FINALISEE");
        // L'ordre de l'historique est lisible sans ambiguite : le dernier est le statut final.
        assertThat(jdbc.queryForObject("""
                select nouveau_statut from "TB_ECHO_FACTURE_HISTORIQUE" where facture_id = ?
                 order by horodatage_reel desc limit 1""", String.class, ecrite.facture().id()))
                .isEqualTo("FINALISEE");
    }

    @Test
    void rejouerLeMemeLotNeCreeAucunDoublon() {
        UUID run = SupportRun.nouveauRun(jdbc);
        FactureEcrite ecrite = unParcours(run);
        List<SimEvent> evenements = List.of(evenement(run, ecrite.facture().id(), "FACTURE_OUVERTE", 1));
        DatabaseSink sink = sink();

        sink.ecrireLot(evenements, List.of(ecrite));
        sink.ecrireLot(evenements, List.of(ecrite));

        assertThat(compter("facture", run)).isEqualTo(1);
        assertThat(compter("facture_ligne", run)).isEqualTo(1);
        assertThat(compter("facture_historique", run)).isEqualTo(5);
        assertThat(compter("evenement", run)).isEqualTo(1);
    }

    @Test
    void siLesEvenementsNePassentPasLaFactureNEstPasEcrite() {
        UUID run = SupportRun.nouveauRun(jdbc);
        FactureEcrite ecrite = unParcours(run);
        UUID runInconnu = UUID.randomUUID();   // viole la cle etrangere de evenement -> tout le lot est annule
        List<SimEvent> evenements = List.of(evenement(runInconnu, ecrite.facture().id(), "FACTURE_OUVERTE", 1));

        assertThatThrownBy(() -> sink().ecrireLot(evenements, List.of(ecrite)))
                .hasMessageContaining("evenement_run_id_fkey");

        assertThat(compter("facture", run)).as("pas de facture sans ses evenements").isZero();
        assertThat(compter("facture_ligne", run)).isZero();
        assertThat(compter("facture_historique", run)).isZero();
    }

    @Test
    void unLotNeContenantQueDesFacturesEstEcrit() {
        UUID run = SupportRun.nouveauRun(jdbc);
        FactureEcrite ecrite = unParcours(run);

        sink().ecrireLot(List.of(), List.of(ecrite));

        assertThat(compter("facture", run)).isEqualTo(1);
        assertThat(compter("evenement", run)).isZero();
    }

    @Test
    void parLaFileLaFactureEstEcriteAvecSesEvenementsEtLeCheckpointNeCompteQueLesEvenements() {
        UUID run = SupportRun.nouveauRun(jdbc);
        DatabaseSink sink = sink();
        sink.demarrer();
        try {
            List<FactureEcrite> factures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                FactureEcrite ecrite = unParcours(run);
                factures.add(ecrite);
                // Comme le tampon du moteur : la facture d'abord, puis les evenements du parcours.
                sink.joindre(ecrite);
                sink.emettre(evenement(run, ecrite.facture().id(), "FACTURE_OUVERTE", 1));
                sink.emettre(evenement(run, ecrite.facture().id(), "FACTURE_FINALISEE", 2));
            }
            sink.flush();

            assertThat(compter("facture", run)).isEqualTo(50);
            assertThat(compter("facture_ligne", run)).isEqualTo(50);
            assertThat(compter("evenement", run)).isEqualTo(100);
            assertThat(jdbc.queryForObject("select compteur from \"TB_ECHO_CHECKPOINT_EVENEMENT\" where run_id = ?", Long.class,
                    run)).isEqualTo(100);
            assertThat(jdbc.queryForObject("""
                    select count(*) from "TB_ECHO_EVENEMENT" e where e.run_id = ?
                       and not exists (select 1 from "TB_ECHO_FACTURE" f where f.id = e.facture_id)""", Long.class, run))
                    .as("aucun evenement sans sa facture").isZero();
        } finally {
            sink.close();
        }
    }

    @Test
    void uneEcritureAnnexeSansEcrivainEstRefuseAvantD_entrerDansLaFile() {
        record Inconnue() implements EcritureAnnexe {
        }
        DatabaseSink sink = sink();
        assertThatThrownBy(() -> sink.joindre(new Inconnue())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun ecrivain");
    }

    @Test
    void lesNumerosDeFactureViennentDeLaSequenceParBlocsSansDoublon() {
        NumerotationFactures blocs = new NumerotationFactures(jdbc);
        long premier = blocs.suivant();
        long deuxieme = blocs.suivant();
        assertThat(deuxieme).isEqualTo(premier + 1);
        // Un autre consommateur de la sequence ne recoit jamais un numero du bloc reserve.
        long autre = jdbc.queryForObject("select nextval('seq_numero_facture')", Long.class);
        assertThat(autre).isGreaterThan(premier + NumerotationFactures.BLOC - 3);
    }
}

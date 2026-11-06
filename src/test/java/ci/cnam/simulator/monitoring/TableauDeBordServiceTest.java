package ci.cnam.simulator.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.monitoring.EtatTableauDeBord.ProxyVue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TableauDeBordServiceTest {

    private final RunManager runs = mock(RunManager.class);
    private final ScenarioService scenarios = mock(ScenarioService.class);
    private final InfrastructureEtat infra = mock(InfrastructureEtat.class);
    private final SimpleMeterRegistry registre = new SimpleMeterRegistry();
    private final TableauDeBordService service = new TableauDeBordService(runs, scenarios, registre, infra,
            Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC));
    private final UUID id = UUID.randomUUID();

    private RunInfo run(long parcours, long emis) {
        return new RunInfo(id, "parcours-normal", 42L, EtatRun.EN_COURS, parcours, emis, Instant.now(), null, null);
    }

    @Test
    @DisplayName("Sans run actif : etat vide, debits vides, base et proxy repris de la sonde")
    void sansRun() {
        when(runs.actif()).thenReturn(Optional.empty());
        when(infra.instantane()).thenReturn(new InfrastructureEtat.Instantane("UP", new ProxyVue("ACTIF", List.of())));

        EtatTableauDeBord etat = service.echantillonner();

        assertThat(etat.run()).isNull();
        assertThat(etat.debits()).isEmpty();
        assertThat(etat.base()).isEqualTo("UP");
        assertThat(etat.proxy().etat()).isEqualTo("ACTIF");
        assertThat(etat.evenementsEcritsParSeconde()).isZero();
    }

    @Test
    @DisplayName("Avec un run actif : le debit est la difference entre deux echantillons, rattache au domaine du scenario")
    void debitMesure() throws InterruptedException {
        when(infra.instantane()).thenReturn(
                new InfrastructureEtat.Instantane("DOWN", new ProxyVue("COUPE", List.of("latence"))));
        when(scenarios.charger("parcours-normal")).thenReturn(new Scenario("parcours-normal", 42L, 1.0,
                LocalDateTime.parse("2026-03-10T08:00:00"), Map.of("prestations", new ConfigDomaine(5, Map.of())),
                List.of(), "nom: x"));
        Counter ecrits = Counter.builder("simulateur.evenements.ecrits").register(registre);
        when(runs.actif()).thenReturn(Optional.of(run(0, 0)));
        service.echantillonner();   // premier echantillon : pas de difference possible

        Thread.sleep(200);
        ecrits.increment(100);
        when(runs.actif()).thenReturn(Optional.of(run(10, 50)));
        EtatTableauDeBord etat = service.echantillonner();

        assertThat(etat.run().etat()).isEqualTo("EN_COURS");
        assertThat(etat.debits()).hasSize(1);
        assertThat(etat.debits().get(0).domaine()).isEqualTo("prestations");
        assertThat(etat.debits().get(0).parcoursParSeconde()).isBetween(10 / 5.0, 10 / 0.19);
        assertThat(etat.debits().get(0).evenementsParSeconde()).isBetween(50 / 5.0, 50 / 0.19);
        assertThat(etat.evenementsEcritsParSeconde()).isBetween(100 / 5.0, 100 / 0.19);
        assertThat(etat.evenementsEcrits()).isEqualTo(100);
        assertThat(etat.base()).isEqualTo("DOWN");
        assertThat(etat.proxy().toxics()).containsExactly("latence");
    }
}

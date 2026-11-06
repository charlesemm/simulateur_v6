package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.chaos.ChaosType;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.security.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PilotageServiceTest {

    private final RunManager runs = mock(RunManager.class);
    private final ScenarioService scenarios = mock(ScenarioService.class);
    private final ChaosInjector chaos = mock(ChaosInjector.class);
    private final AuditService audit = mock(AuditService.class);
    private final PilotageService pilotage = new PilotageService(runs, scenarios, chaos, audit);

    private final Scenario base = new Scenario("parcours-normal", 42L, 60.0,
            LocalDateTime.parse("2026-03-10T08:00:00"), Map.of(), List.of(), "nom: parcours-normal");

    private static RunInfo run(UUID id, long seed) {
        return new RunInfo(id, "parcours-normal", seed, EtatRun.EN_COURS, 0, 0, Instant.now(), null, null);
    }

    @BeforeEach
    void preparer() {
        when(runs.actif()).thenReturn(Optional.empty());
        when(scenarios.charger("parcours-normal")).thenReturn(base);
        when(runs.lancer(any())).thenAnswer(i -> run(UUID.randomUUID(), ((Scenario) i.getArgument(0)).seed()));
    }

    @Test
    @DisplayName("Graine et vitesse saisies remplacent celles du scenario ; le reste est inchange ; lancement audite")
    void lancerAvecSurcharges() {
        pilotage.lancer("parcours-normal", 1234L, 100.0, "alice");

        ArgumentCaptor<Scenario> capte = ArgumentCaptor.forClass(Scenario.class);
        verify(runs).lancer(capte.capture());
        assertThat(capte.getValue().seed()).isEqualTo(1234L);
        assertThat(capte.getValue().accelerationTemps()).isEqualTo(100.0);
        assertThat(capte.getValue().nom()).isEqualTo("parcours-normal");
        verify(audit).enregistrer(eq("alice"), eq("RUN_LANCE"), any(), any());
    }

    @Test
    @DisplayName("Sans graine ni vitesse, le scenario est lance tel quel")
    void lancerSansSurcharge() {
        pilotage.lancer("parcours-normal", null, null, "alice");

        ArgumentCaptor<Scenario> capte = ArgumentCaptor.forClass(Scenario.class);
        verify(runs).lancer(capte.capture());
        assertThat(capte.getValue().seed()).isEqualTo(42L);
        assertThat(capte.getValue().accelerationTemps()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("Refus : scenario non choisi, vitesse invalide, ou un run deja actif (rien n'est lance)")
    void refus() {
        assertThatThrownBy(() -> pilotage.lancer(" ", null, null, "a")).hasMessageContaining("scenario");
        assertThatThrownBy(() -> pilotage.lancer("parcours-normal", null, -1.0, "a"))
                .hasMessageContaining("Vitesse");
        when(runs.actif()).thenReturn(Optional.of(run(UUID.randomUUID(), 1)));
        assertThatThrownBy(() -> pilotage.lancer("parcours-normal", null, null, "a"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("deja actif");
        verify(runs, never()).lancer(any());
    }

    @Test
    @DisplayName("Rejouer relance le scenario du run d'origine avec SA graine")
    void rejouer() {
        UUID ancien = UUID.randomUUID();
        when(runs.info(ancien)).thenReturn(run(ancien, 777L));

        pilotage.rejouer(ancien, "alice");

        ArgumentCaptor<Scenario> capte = ArgumentCaptor.forClass(Scenario.class);
        verify(runs).lancer(capte.capture());
        assertThat(capte.getValue().seed()).isEqualTo(777L);
        verify(audit).enregistrer(eq("alice"), eq("RUN_REJOUE"), any(), any());
    }

    @Test
    @DisplayName("Chaos : les parametres envoyes au moteur dependent du type, l'action est auditee")
    void chaos() {
        UUID id = UUID.randomUUID();
        when(chaos.declencher(eq(id), any(), any(), any(), any())).thenAnswer(i -> new ChaosAction(
                UUID.randomUUID(), id, i.getArgument(1), i.getArgument(3), Instant.now(), Instant.now(), null, null,
                "alice", 0));

        pilotage.declencher(id, new DemandeChaos(ChaosType.LATENCE, 30, "ignore", 500, 50, 20, 0.5), "alice");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parametres = ArgumentCaptor.forClass(Map.class);
        verify(chaos).declencher(eq(id), eq(ChaosType.LATENCE), eq(Duration.ofSeconds(30)), parametres.capture(),
                eq("alice"));
        assertThat(parametres.getValue()).containsOnlyKeys("latenceMs", "jitterMs")
                .containsEntry("latenceMs", 500).containsEntry("jitterMs", 50);
        verify(audit).enregistrer(eq("alice"), eq("CHAOS_DECLENCHE"), eq(id.toString()), any());
    }

    @Test
    @DisplayName("Chaos : type et duree obligatoires")
    void chaosIncomplet() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> pilotage.declencher(id, new DemandeChaos(null, 30, null, null, null, null, null), "a"))
                .hasMessageContaining("type");
        assertThatThrownBy(() -> pilotage.declencher(id,
                new DemandeChaos(ChaosType.SURCHARGE, null, null, null, null, 5, null), "a"))
                .hasMessageContaining("duree");
    }

    @Test
    @DisplayName("Tout retablir est audite avec le nombre d'actions annulees")
    void retablir() {
        UUID id = UUID.randomUUID();
        when(chaos.retablirTout(id, "alice")).thenReturn(List.of());

        pilotage.retablir(id, "alice");

        verify(audit).enregistrer("alice", "CHAOS_RETABLI", id.toString(), "0 action(s) annulee(s)");
    }
}

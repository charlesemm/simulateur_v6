package ci.cnam.simulator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.sink.InMemoryEventSink;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {

    private static final UUID RUN = new UUID(7, 7);
    private final ScenarioLoader loader = new ScenarioLoader();
    private SimulationEngine moteurEnCours;

    @AfterEach
    void nettoyer() {
        if (moteurEnCours != null) {
            moteurEnCours.arreter();
        }
    }

    private Scenario scenario(int debit, long seed) {
        return loader.lire("""
                nom: test
                seed: %d
                accelerationTemps: 100
                domaines:
                  prestations: { debit: %d }
                """.formatted(seed, debit));
    }

    private SimulationEngine moteur(Scenario s, InMemoryEventSink sink, AtomicReference<Throwable> echec) {
        VirtualClock horloge = new VirtualClock(s.debutSimule().toInstant(ZoneOffset.UTC), s.accelerationTemps());
        moteurEnCours = new SimulationEngine(RUN, s, Map.of("prestations", new GenerateurFactice("prestations")),
                sink, horloge, echec::set);
        return moteurEnCours;
    }

    private static void attendre(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    @Test
    void produitAuDebitDemandeAdixPourCentPres() throws Exception {
        InMemoryEventSink sink = new InMemoryEventSink();
        SimulationEngine moteur = moteur(scenario(200, 1), sink, new AtomicReference<>());

        long debut = System.nanoTime();
        moteur.demarrer();
        attendre(2000);
        moteur.arreter();
        double secondes = (System.nanoTime() - debut) / 1e9;

        double observe = sink.evenements().size() / secondes;
        assertThat(observe).isBetween(200 * 0.9, 200 * 1.1);
        assertThat(moteur.parcoursGeneres()).isEqualTo(sink.evenements().size());
        assertThat(moteur.evenementsEmis()).isEqualTo(sink.evenements().size());
    }

    @Test
    void pauseArreteLesEvenementsRepriseLesRelanceEtArretEstPropre() throws Exception {
        InMemoryEventSink sink = new InMemoryEventSink();
        AtomicReference<Throwable> echec = new AtomicReference<>();
        SimulationEngine moteur = moteur(scenario(200, 1), sink, echec);

        moteur.demarrer();
        attendre(300);
        assertThat(sink.evenements()).isNotEmpty();

        moteur.pause();
        int figes = sink.evenements().size();
        attendre(400);
        assertThat(sink.evenements()).hasSize(figes);

        moteur.reprendre();
        attendre(400);
        assertThat(sink.evenements().size()).isGreaterThan(figes);

        moteur.arreter();
        int finaux = sink.evenements().size();
        attendre(300);
        assertThat(sink.evenements()).hasSize(finaux);
        assertThat(echec.get()).isNull();
    }

    @Test
    void laPauseNeCompteNiDansLeDebitNiDansLesPhases() throws Exception {
        // Apres 400 ms de pause, la reprise ne doit pas "rattraper" : le total reste proche du temps actif.
        InMemoryEventSink sink = new InMemoryEventSink();
        SimulationEngine moteur = moteur(scenario(200, 1), sink, new AtomicReference<>());

        moteur.demarrer();
        attendre(500);
        moteur.pause();
        attendre(1000);
        moteur.reprendre();
        attendre(500);
        moteur.arreter();

        // ~1 s de temps actif a 200/s = ~200, loin des ~400 qu'un rattrapage donnerait.
        assertThat(sink.evenements().size()).isBetween(170, 230);
    }

    @Test
    void memeScenarioEtMemeGraineDonnentLaMemeSuiteDEvenements() throws Exception {
        List<SimEvent> premiere = executer(scenario(500, 123));
        List<SimEvent> deuxieme = executer(scenario(500, 123));
        List<SimEvent> autre = executer(scenario(500, 124));

        int n = Math.min(premiere.size(), deuxieme.size());
        assertThat(n).isGreaterThan(100);
        assertThat(deuxieme.subList(0, n)).isEqualTo(premiere.subList(0, n));
        assertThat(autre.subList(0, 50)).isNotEqualTo(premiere.subList(0, 50));
    }

    @Test
    void lesDatesSimuleesSuiventLeScenarioEtPasLaVitesseDeLaMachine() throws Exception {
        List<SimEvent> evenements = executer(scenario(100, 5));

        // debit 100/s, x100 : le k-ieme parcours est a k/100 s reelles = k secondes simulees.
        for (int k = 0; k < 20; k++) {
            assertThat(evenements.get(k).timestampSimule())
                    .isEqualTo(ScenarioLoader.DEBUT_SIMULE_PAR_DEFAUT.plusSeconds(k));
        }
    }

    @Test
    void unGenerateurEnErreurFaitEchouerLeRunSansBloquer() throws Exception {
        AtomicReference<Throwable> echec = new AtomicReference<>();
        Scenario s = scenario(100, 1);
        VirtualClock horloge = new VirtualClock(Instant.parse("2026-03-10T08:00:00Z"), 1);
        moteurEnCours = new SimulationEngine(RUN, s, Map.of("prestations", new GenerateurFactice("prestations") {
            @Override
            public void generer(ContexteGeneration ctx) {
                throw new IllegalStateException("boum");
            }
        }), new InMemoryEventSink(), horloge, echec::set);

        moteurEnCours.demarrer();
        attendre(300);

        assertThat(echec.get()).hasMessage("boum");
        assertThat(horloge.enMarche()).isFalse();
    }

    @Test
    void refuseUnDomaineSansGenerateur() {
        Scenario s = loader.lire("nom: a\nseed: 1\ndomaines: {affiliation: {debit: 5}}");
        VirtualClock horloge = new VirtualClock(Instant.parse("2026-03-10T08:00:00Z"), 1);

        assertThatThrownBy(() -> new SimulationEngine(RUN, s, Map.of("prestations", new GenerateurFactice("prestations")),
                new InMemoryEventSink(), horloge, t -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Domaine inconnu : 'affiliation'");
    }

    private List<SimEvent> executer(Scenario s) throws InterruptedException {
        InMemoryEventSink sink = new InMemoryEventSink();
        SimulationEngine moteur = moteur(s, sink, new AtomicReference<>());
        moteur.demarrer();
        attendre(1200);
        moteur.arreter();
        return sink.evenements();
    }
}

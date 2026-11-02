package ci.cnam.simulator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.sink.InMemoryEventSink;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunManagerTest {

    private static final class StoreMemoire implements RunStore {
        final List<String> journal = new ArrayList<>();

        @Override
        public void creer(UUID runId, Scenario scenario) {
            journal.add("CREE");
        }

        @Override
        public void mettreAJour(UUID runId, EtatRun etat, Instant debut, Instant fin, String erreur) {
            journal.add(etat.name());
        }
    }

    private final ScenarioLoader loader = new ScenarioLoader();
    private final InMemoryEventSink sink = new InMemoryEventSink();
    private StoreMemoire store;
    private RunManager manager;
    private Scenario scenario;

    @BeforeEach
    void preparer() {
        store = new StoreMemoire();
        manager = new RunManager(List.of(new GenerateurFactice("prestations")), sink, store, Clock.systemUTC());
        scenario = loader.lire("nom: t\nseed: 1\ndomaines: {prestations: {debit: 100}}");
    }

    @AfterEach
    void nettoyer() {
        manager.actif().ifPresent(r -> manager.arreter(r.id()));
    }

    @Test
    void cycleDeVieCompletEtJournalDesEtats() throws Exception {
        RunInfo cree = manager.creer(scenario);
        assertThat(cree.etat()).isEqualTo(EtatRun.CREE);

        assertThat(manager.demarrer(cree.id()).etat()).isEqualTo(EtatRun.EN_COURS);
        Thread.sleep(200);
        assertThat(manager.info(cree.id()).evenementsEmis()).isPositive();

        assertThat(manager.pause(cree.id()).etat()).isEqualTo(EtatRun.EN_PAUSE);
        long fige = manager.info(cree.id()).evenementsEmis();
        Thread.sleep(200);
        assertThat(manager.info(cree.id()).evenementsEmis()).isEqualTo(fige);

        assertThat(manager.reprendre(cree.id()).etat()).isEqualTo(EtatRun.EN_COURS);
        RunInfo arrete = manager.arreter(cree.id());

        assertThat(arrete.etat()).isEqualTo(EtatRun.ARRETE);
        assertThat(arrete.debut()).isNotNull();
        assertThat(arrete.fin()).isNotNull();
        assertThat(manager.actif()).isEmpty();
        assertThat(store.journal).containsExactly("CREE", "EN_COURS", "EN_PAUSE", "EN_COURS", "ARRETE");
    }

    @Test
    void unSeulRunActifALaFois() {
        RunInfo premier = manager.creer(scenario);
        RunInfo second = manager.creer(scenario);
        manager.demarrer(premier.id());

        assertThatThrownBy(() -> manager.demarrer(second.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("deja actif");

        manager.pause(premier.id());
        assertThatThrownBy(() -> manager.demarrer(second.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("deja actif");

        manager.arreter(premier.id());
        assertThat(manager.demarrer(second.id()).etat()).isEqualTo(EtatRun.EN_COURS);
    }

    @Test
    void refuseLesTransitionsInterdites() {
        RunInfo run = manager.creer(scenario);

        assertThatThrownBy(() -> manager.pause(run.id())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> manager.reprendre(run.id())).isInstanceOf(IllegalStateException.class);
        manager.demarrer(run.id());
        assertThatThrownBy(() -> manager.demarrer(run.id())).isInstanceOf(IllegalStateException.class);
        manager.arreter(run.id());
        assertThatThrownBy(() -> manager.reprendre(run.id())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> manager.arreter(run.id())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> manager.info(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuseAuCreateUnDomaineInconnuSansRienEcrire() {
        Scenario inconnu = loader.lire("nom: t\nseed: 1\ndomaines: {recouvrement: {debit: 1}}");

        assertThatThrownBy(() -> manager.creer(inconnu)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.journal).isEmpty();
    }

    @Test
    void unGenerateurEnErreurMetLeRunAEchoue() throws Exception {
        RunManager casse = new RunManager(List.of(new GenerateurFactice("prestations") {
            @Override
            public void generer(ContexteGeneration ctx) {
                throw new IllegalStateException("panne");
            }
        }), sink, store, Clock.systemUTC());
        RunInfo run = casse.creer(scenario);
        casse.demarrer(run.id());
        Thread.sleep(300);

        RunInfo apres = casse.info(run.id());
        assertThat(apres.etat()).isEqualTo(EtatRun.ECHOUE);
        assertThat(apres.erreur()).contains("panne");
        assertThat(casse.actif()).isEmpty();
    }
}

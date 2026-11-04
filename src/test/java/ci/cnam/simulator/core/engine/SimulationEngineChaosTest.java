package ci.cnam.simulator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.sink.InMemoryEventSink;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Le moteur face au chaos : multiplicateur de charge, parcours tout ou rien, rejeu apres indisponibilite. */
class SimulationEngineChaosTest {

    private static final UUID RUN = new UUID(9, 9);
    private final ScenarioLoader loader = new ScenarioLoader();
    private SimulationEngine moteurEnCours;

    @AfterEach
    void nettoyer() {
        if (moteurEnCours != null) {
            moteurEnCours.arreter();
        }
    }

    /** Panne simulee : une erreur que l'execution atomique reconnait comme une indisponibilite. */
    private static final class Panne extends RuntimeException {
        Panne() {
            super("liaison coupee");
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

    private SimulationEngine moteur(Scenario s, InMemoryEventSink sink, AtomicReference<Throwable> echec,
                                    ExecutionAtomique atomique, MultiplicateurCharge charge) {
        VirtualClock horloge = new VirtualClock(s.debutSimule().toInstant(ZoneOffset.UTC), s.accelerationTemps());
        moteurEnCours = new SimulationEngine(RUN, s, Map.of("prestations", new GenerateurFactice("prestations")),
                sink, horloge, echec::set, atomique, charge);
        return moteurEnCours;
    }

    @Test
    void leMultiplicateurDeChargeAugmenteLeDebitDemande() throws Exception {
        InMemoryEventSink normal = new InMemoryEventSink();
        InMemoryEventSink surcharge = new InMemoryEventSink();

        SimulationEngine a = moteur(scenario(50, 1), normal, new AtomicReference<>(), ExecutionAtomique.DIRECTE,
                MultiplicateurCharge.AUCUN);
        a.demarrer();
        Thread.sleep(1000);
        a.arreter();

        SimulationEngine b = moteur(scenario(50, 1), surcharge, new AtomicReference<>(), ExecutionAtomique.DIRECTE,
                () -> 20.0);
        b.demarrer();
        Thread.sleep(1000);
        b.arreter();

        assertThat(normal.evenements().size()).isBetween(35, 65);
        assertThat(surcharge.evenements().size()).isGreaterThan(10 * normal.evenements().size());
    }

    @Test
    void leMultiplicateurPeutChangerEnCoursDeRunEtRevenirANormal() throws Exception {
        InMemoryEventSink sink = new InMemoryEventSink();
        double[] facteur = {1.0};
        SimulationEngine moteur = moteur(scenario(50, 1), sink, new AtomicReference<>(), ExecutionAtomique.DIRECTE,
                () -> facteur[0]);
        moteur.demarrer();
        Thread.sleep(500);
        int avant = sink.evenements().size();
        facteur[0] = 20;
        Thread.sleep(500);
        int pendant = sink.evenements().size() - avant;
        facteur[0] = 1;
        Thread.sleep(500);
        int apres = sink.evenements().size() - avant - pendant;

        assertThat(pendant).isGreaterThan(5 * Math.max(avant, 1));
        assertThat(apres).isLessThan(pendant / 3);   // retour au debit normal : plus de rattrapage artificiel
    }

    @Test
    void uneIndisponibiliteAnnuleLeParcoursEtLeRejoueALIdentiqueSansEvenementOrphelin() throws Exception {
        // Reference : aucune panne.
        InMemoryEventSink propre = new InMemoryEventSink();
        moteur(scenario(200, 77), propre, new AtomicReference<>(), ExecutionAtomique.DIRECTE,
                MultiplicateurCharge.AUCUN).demarrer();
        await().atMost(Duration.ofSeconds(5)).until(() -> propre.evenements().size() >= 60);
        moteurEnCours.arreter();

        // Avec pannes : les 3 premieres executions et quelques autres echouent APRES avoir emis leur evenement.
        InMemoryEventSink eprouve = new InMemoryEventSink();
        AtomicInteger appels = new AtomicInteger();
        ExecutionAtomique fragile = new ExecutionAtomique() {
            @Override
            public void executer(Runnable travail) {
                travail.run();   // le generateur a deja envoye son evenement au tampon...
                int n = appels.incrementAndGet();
                if (n <= 3 || n == 10 || n == 11) {
                    throw new Panne();   // ... et la base tombe avant la validation
                }
            }

            @Override
            public boolean estIndisponibilite(Throwable erreur) {
                return erreur instanceof Panne;
            }

            @Override
            public long patienceMs() {
                return 10_000;
            }
        };
        AtomicReference<Throwable> echec = new AtomicReference<>();
        SimulationEngine moteur = moteur(scenario(200, 77), eprouve, echec, fragile, MultiplicateurCharge.AUCUN);
        moteur.demarrer();
        await().atMost(Duration.ofSeconds(10)).until(() -> eprouve.evenements().size() >= 60);
        moteur.arreter();

        assertThat(echec.get()).isNull();
        assertThat(moteur.parcoursRejoues()).isEqualTo(5);
        List<SimEvent> attendus = propre.evenements().subList(0, 60);
        List<SimEvent> obtenus = eprouve.evenements().subList(0, 60);
        // Memes evenements, memes identifiants, dans le meme ordre : un rejeu est invisible de l'exterieur.
        assertThat(obtenus).isEqualTo(attendus);
        assertThat(eprouve.evenements().stream().map(SimEvent::eventId).distinct().count())
                .isEqualTo(eprouve.evenements().size());
    }

    @Test
    void uneIndisponibiliteTropLongueFaitEchouerLeRun() {
        ExecutionAtomique toujoursEnPanne = new ExecutionAtomique() {
            @Override
            public void executer(Runnable travail) {
                throw new Panne();
            }

            @Override
            public boolean estIndisponibilite(Throwable erreur) {
                return erreur instanceof Panne;
            }

            @Override
            public long patienceMs() {
                return 300;
            }
        };
        AtomicReference<Throwable> echec = new AtomicReference<>();
        InMemoryEventSink sink = new InMemoryEventSink();
        moteur(scenario(100, 1), sink, echec, toujoursEnPanne, MultiplicateurCharge.AUCUN).demarrer();

        await().atMost(Duration.ofSeconds(5)).until(() -> echec.get() != null);
        assertThat(echec.get()).isInstanceOf(Panne.class);
        assertThat(sink.evenements()).isEmpty();   // rien n'a fuite d'un parcours jamais valide
    }

    @Test
    void uneVraieErreurNEstJamaisRejouee() {
        AtomicInteger appels = new AtomicInteger();
        ExecutionAtomique contrainteViolee = new ExecutionAtomique() {
            @Override
            public void executer(Runnable travail) {
                appels.incrementAndGet();
                throw new IllegalStateException("contrainte violee");
            }

            @Override
            public boolean estIndisponibilite(Throwable erreur) {
                return false;
            }

            @Override
            public long patienceMs() {
                return 60_000;
            }
        };
        AtomicReference<Throwable> echec = new AtomicReference<>();
        moteur(scenario(100, 1), new InMemoryEventSink(), echec, contrainteViolee, MultiplicateurCharge.AUCUN)
                .demarrer();

        await().atMost(Duration.ofSeconds(5)).until(() -> echec.get() != null);
        assertThat(echec.get()).hasMessageContaining("contrainte violee");
        assertThat(appels.get()).isEqualTo(1);
    }

    @Test
    void arreterPendantUnePanneAbandonneLeParcoursSansBloquer() throws Exception {
        ExecutionAtomique toujoursEnPanne = new ExecutionAtomique() {
            @Override
            public void executer(Runnable travail) {
                throw new Panne();
            }

            @Override
            public boolean estIndisponibilite(Throwable erreur) {
                return true;
            }

            @Override
            public long patienceMs() {
                return 60_000;
            }
        };
        AtomicReference<Throwable> echec = new AtomicReference<>();
        InMemoryEventSink sink = new InMemoryEventSink();
        SimulationEngine moteur = moteur(scenario(100, 1), sink, echec, toujoursEnPanne, MultiplicateurCharge.AUCUN);
        moteur.demarrer();
        Thread.sleep(400);

        long debut = System.nanoTime();
        moteur.pause();       // ne doit pas attendre la fin de la panne
        moteur.reprendre();
        moteur.arreter();
        long ms = (System.nanoTime() - debut) / 1_000_000;

        assertThat(ms).isLessThan(2_000);
        assertThat(echec.get()).isNull();
        assertThat(sink.evenements()).isEmpty();
    }
}

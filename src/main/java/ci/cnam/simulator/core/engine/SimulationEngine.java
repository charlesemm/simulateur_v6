package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.domain.FlowGenerator;
import ci.cnam.simulator.sink.EventSink;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Pilote un run : un fil d'execution qui declenche les parcours a l'instant prevu par la {@link Cadence}
 * (debit et phases), les date avec la {@link VirtualClock}, et les confie aux generateurs.
 * Un moteur = un run. Le moteur ne connait aucun domaine : il ne voit que {@link FlowGenerator}.
 *
 * <p>Reproductibilite : l'instant, la date simulee et l'alea de chaque parcours ne dependent que du scenario
 * et de la graine. La vitesse de la machine change le moment ou l'on calcule, pas ce que l'on calcule.
 * Si la machine ne suit pas le debit, le moteur rattrape son retard sans jamais sauter de parcours
 * (la file bornee de la brique 7 ralentira proprement).
 */
public final class SimulationEngine {

    private static final long PAUSE_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private static final long ATTENTE_MAX_NANOS = TimeUnit.MILLISECONDS.toNanos(2);
    private static final long ARRET_ATTENTE_MS = 10_000;

    private static final class Domaine {
        final String nom;
        final FlowGenerator generateur;
        final ConfigDomaine config;
        final Cadence cadence;
        final SeededRandom random;
        long generes;

        Domaine(String nom, FlowGenerator generateur, ConfigDomaine config, Cadence cadence, SeededRandom random) {
            this.nom = nom;
            this.generateur = generateur;
            this.config = config;
            this.cadence = cadence;
            this.random = random;
        }

        double prochainInstant() {
            return cadence.instant(generes);
        }
    }

    private final UUID runId;
    private final VirtualClock horloge;
    private final EventSink destination;
    private final Consumer<Throwable> surEchec;
    private final List<Domaine> domaines = new ArrayList<>();
    // Equitable : pause() et arreter() passent apres le parcours en cours, meme si le moteur est en retard.
    private final ReentrantLock verrou = new ReentrantLock(true);
    private final AtomicLong parcoursGeneres = new AtomicLong();
    private final AtomicLong evenementsEmis = new AtomicLong();
    private final EventSink compteur = new EventSink() {
        @Override
        public void emettre(SimEvent evenement) {
            destination.emettre(evenement);
            evenementsEmis.incrementAndGet();
        }
    };

    private volatile boolean arret;
    private boolean enPause;   // protege par verrou
    private Thread fil;

    /**
     * @param generateurs generateurs disponibles, par nom de domaine ; chaque domaine du scenario doit en avoir un
     * @param surEchec    appele depuis le fil du moteur si un generateur leve une exception (le run s'arrete)
     */
    public SimulationEngine(UUID runId, Scenario scenario, Map<String, FlowGenerator> generateurs,
                            EventSink destination, VirtualClock horloge, Consumer<Throwable> surEchec) {
        this.runId = runId;
        this.horloge = horloge;
        this.destination = destination;
        this.surEchec = surEchec;
        scenario.domaines().forEach((nom, config) -> {
            FlowGenerator generateur = generateurs.get(nom);
            if (generateur == null) {
                throw new IllegalArgumentException("Domaine inconnu : '" + nom + "' (domaines disponibles : "
                        + generateurs.keySet() + ")");
            }
            generateur.valider(config);
            // Alea propre a chaque domaine : String.hashCode() est stable d'une JVM a l'autre.
            SeededRandom random = new SeededRandom(scenario.seed() * 31 + nom.hashCode());
            domaines.add(new Domaine(nom, generateur, config, new Cadence(config.debit(), scenario.phases()), random));
        });
        if (domaines.isEmpty()) {
            throw new IllegalArgumentException("Le scenario ne contient aucun domaine a simuler");
        }
    }

    public UUID runId() {
        return runId;
    }

    public long parcoursGeneres() {
        return parcoursGeneres.get();
    }

    public long evenementsEmis() {
        return evenementsEmis.get();
    }

    public synchronized void demarrer() {
        if (fil != null) {
            throw new IllegalStateException("Moteur deja demarre");
        }
        horloge.demarrer();
        fil = new Thread(this::boucle, "simulation-" + runId.toString().substring(0, 8));
        fil.setDaemon(true);
        fil.start();
    }

    /** Synchrone : au retour, plus aucun parcours n'est en cours ni ne sera produit avant la reprise. */
    public void pause() {
        verrou.lock();
        try {
            enPause = true;
            horloge.pause();
        } finally {
            verrou.unlock();
        }
    }

    public void reprendre() {
        verrou.lock();
        try {
            enPause = false;
            horloge.reprendre();
        } finally {
            verrou.unlock();
        }
    }

    /** Arrete le fil et fige l'horloge. Attend la fin du parcours en cours (10 s au plus). */
    public void arreter() {
        arret = true;
        Thread f;
        synchronized (this) {
            f = fil;
        }
        if (f != null && f != Thread.currentThread()) {
            LockSupport.unpark(f);
            try {
                f.join(ARRET_ATTENTE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        horloge.pause();
    }

    private void boucle() {
        try {
            while (!arret) {
                long attente = 0;
                verrou.lock();
                try {
                    if (arret) {
                        break;
                    }
                    if (enPause) {
                        attente = PAUSE_NANOS;
                    } else {
                        attente = unPas();
                    }
                } finally {
                    verrou.unlock();
                }
                if (attente > 0) {
                    LockSupport.parkNanos(attente);
                }
            }
        } catch (Throwable t) {
            arret = true;
            horloge.pause();
            surEchec.accept(t);
        }
    }

    /** Genere le parcours le plus en retard s'il est du ; sinon renvoie la duree a attendre (nanos). */
    private long unPas() {
        Duration actif = horloge.tempsActif();
        double maintenant = actif.toNanos() / 1e9;
        Domaine choisi = null;
        double instantChoisi = Double.POSITIVE_INFINITY;
        for (Domaine d : domaines) {
            double instant = d.prochainInstant();
            if (instant < instantChoisi) {
                instantChoisi = instant;
                choisi = d;
            }
        }
        if (instantChoisi > maintenant) {
            long ecart = (long) ((instantChoisi - maintenant) * 1e9);
            return Math.max(1, Math.min(ecart, ATTENTE_MAX_NANOS));
        }
        // Date simulee = celle du moment PREVU (pas du moment de calcul) : reproductible.
        Duration prevu = Duration.ofNanos((long) (instantChoisi * 1e9));
        ContexteGeneration ctx = new ContexteGeneration(runId, choisi.random, horloge.figeeA(prevu), choisi.config,
                compteur);
        choisi.generateur.generer(ctx);
        choisi.generes++;
        parcoursGeneres.incrementAndGet();
        return 0;
    }
}

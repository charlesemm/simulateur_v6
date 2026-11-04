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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(SimulationEngine.class);

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
    private final ExecutionAtomique atomique;
    private final MultiplicateurCharge charge;
    private final List<Domaine> domaines = new ArrayList<>();
    // Equitable : pause() et arreter() passent apres le parcours en cours, meme si le moteur est en retard.
    private final ReentrantLock verrou = new ReentrantLock(true);
    private final AtomicLong parcoursGeneres = new AtomicLong();
    private final AtomicLong evenementsEmis = new AtomicLong();
    private final AtomicLong parcoursRejoues = new AtomicLong();
    private final EventSink compteur = new EventSink() {
        @Override
        public void emettre(SimEvent evenement) {
            destination.emettre(evenement);
            evenementsEmis.incrementAndGet();
        }
    };

    private volatile boolean arret;
    private boolean enPause;   // protege par verrou
    private double dernierActif;    // protege par verrou : temps actif lu au pas precedent (secondes)
    private double tempsCharge;     // protege par verrou : temps actif pondere par le multiplicateur de charge
    private Thread fil;

    /**
     * @param generateurs generateurs disponibles, par nom de domaine ; chaque domaine du scenario doit en avoir un
     * @param surEchec    appele depuis le fil du moteur si un generateur leve une exception (le run s'arrete)
     */
    public SimulationEngine(UUID runId, Scenario scenario, Map<String, FlowGenerator> generateurs,
                            EventSink destination, VirtualClock horloge, Consumer<Throwable> surEchec) {
        this(runId, scenario, generateurs, destination, horloge, surEchec, ExecutionAtomique.DIRECTE,
                MultiplicateurCharge.AUCUN);
    }

    /**
     * @param atomique execute chaque parcours en tout ou rien et le rejoue si la destination est indisponible
     * @param charge   multiplicateur de debit (chaos : surcharge)
     */
    public SimulationEngine(UUID runId, Scenario scenario, Map<String, FlowGenerator> generateurs,
                            EventSink destination, VirtualClock horloge, Consumer<Throwable> surEchec,
                            ExecutionAtomique atomique, MultiplicateurCharge charge) {
        this.runId = runId;
        this.atomique = atomique;
        this.charge = charge;
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

    /** Tentatives de parcours annulees puis rejouees parce que la destination etait indisponible. */
    public long parcoursRejoues() {
        return parcoursRejoues.get();
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
        double actif = horloge.tempsActif().toNanos() / 1e9;
        double facteur = Math.max(1e-6, charge.facteur());
        // Le temps "de charge" avance facteur fois plus vite que le temps actif : x20 = 20 fois plus de parcours dus.
        tempsCharge += Math.max(0, actif - dernierActif) * facteur;
        dernierActif = actif;
        double maintenant = tempsCharge;
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
            long ecart = (long) ((instantChoisi - maintenant) / facteur * 1e9);
            return Math.max(1, Math.min(ecart, ATTENTE_MAX_NANOS));
        }
        // Date simulee = celle du moment PREVU (pas du moment de calcul) : reproductible.
        Duration prevu = Duration.ofNanos((long) (instantChoisi * 1e9));
        if (genererParcours(choisi, prevu)) {
            choisi.generes++;
            parcoursGeneres.incrementAndGet();
        }
        return 0;
    }

    /**
     * Un parcours = une unite tout ou rien : ses ecritures sont validees ensemble et ses evenements ne sont emis
     * qu'apres. Si la destination est indisponible, le parcours est annule puis rejoue A L'IDENTIQUE (meme graine) :
     * aucune facture a moitie ecrite, aucun evenement orphelin. Une erreur qui n'est pas une indisponibilite, ou une
     * indisponibilite plus longue que la patience, fait echouer le run comme avant.
     *
     * @return faux si le parcours a ete abandonne parce que le run s'arrete
     */
    private boolean genererParcours(Domaine d, Duration prevu) {
        long graine = d.random.nextLong();   // un tirage par parcours : le rejeu reproduit exactement le meme parcours
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(atomique.patienceMs());
        long pause = TimeUnit.MILLISECONDS.toNanos(100);
        boolean rejoue = false;
        while (true) {
            TamponEvenements tampon = new TamponEvenements();
            ContexteGeneration ctx = new ContexteGeneration(runId, new SeededRandom(graine), horloge.figeeA(prevu),
                    d.config, tampon);
            try {
                atomique.executer(() -> d.generateur.generer(ctx));
            } catch (RuntimeException e) {
                if (!atomique.estIndisponibilite(e) || System.nanoTime() > limite) {
                    throw e;
                }
                if (!rejoue) {
                    LOG.warn("Run {} : destination indisponible, le parcours est annule et sera rejoue ({})", runId,
                            e.getClass().getSimpleName());
                    rejoue = true;
                }
                parcoursRejoues.incrementAndGet();
                if (!attendre(pause)) {
                    return false;
                }
                pause = Math.min(pause * 2, TimeUnit.SECONDS.toNanos(1));
                continue;
            }
            tampon.viderVers(compteur);
            if (rejoue) {
                LOG.info("Run {} : destination revenue, parcours rejoue et valide", runId);
            }
            return true;
        }
    }

    /** Attend en laissant passer pause() et arreter() ; faux si le run s'est arrete pendant l'attente. */
    private boolean attendre(long nanos) {
        verrou.unlock();   // le verrou est equitable : un pause() en attente passe devant
        try {
            LockSupport.parkNanos(nanos);
        } finally {
            verrou.lock();
        }
        return !arret;
    }
}

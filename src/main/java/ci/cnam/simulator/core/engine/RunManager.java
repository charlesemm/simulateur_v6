package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.domain.FlowGenerator;
import ci.cnam.simulator.sink.EventSink;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Cycle de vie des runs : CREE, EN_COURS, EN_PAUSE, ARRETE, ECHOUE. UN SEUL run actif (en cours ou en pause)
 * a la fois dans cette version. Chaque changement d'etat est inscrit dans simulation_run.
 */
@Service
public class RunManager {

    private static final Logger LOG = LoggerFactory.getLogger(RunManager.class);

    private static final class Run {
        final UUID id;
        final Scenario scenario;
        final SimulationEngine moteur;
        EtatRun etat = EtatRun.CREE;
        Instant debut;
        Instant fin;
        String erreur;

        Run(UUID id, Scenario scenario, SimulationEngine moteur) {
            this.id = id;
            this.scenario = scenario;
            this.moteur = moteur;
        }
    }

    private final Map<String, FlowGenerator> generateurs;
    private final EventSink destination;
    private final RunStore store;
    private final Clock horlogeReelle;
    private final ExecutionAtomique atomique;
    private final MultiplicateurCharge charge;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    public RunManager(List<FlowGenerator> generateurs, EventSink destination, RunStore store, Clock horlogeReelle) {
        this(generateurs, destination, store, horlogeReelle, ExecutionAtomique.DIRECTE, MultiplicateurCharge.AUCUN);
    }

    @Autowired
    public RunManager(List<FlowGenerator> generateurs, EventSink destination, RunStore store, Clock horlogeReelle,
                      ExecutionAtomique atomique, MultiplicateurCharge charge) {
        this.atomique = atomique;
        this.charge = charge;
        this.generateurs = generateurs.stream().collect(Collectors.toMap(FlowGenerator::domaine, Function.identity()));
        this.destination = destination;
        this.store = store;
        this.horlogeReelle = horlogeReelle;
    }

    /** Cree un run au statut CREE. Refuse un scenario dont un domaine est inconnu ou mal regle. */
    public RunInfo creer(Scenario scenario) {
        UUID id = UUID.randomUUID();
        VirtualClock horloge = new VirtualClock(scenario.debutSimule().toInstant(ZoneOffset.UTC),
                scenario.accelerationTemps());
        SimulationEngine moteur = new SimulationEngine(id, scenario, generateurs, destination, horloge,
                t -> echec(id, t), atomique, charge);
        store.creer(id, scenario);
        Run run = new Run(id, scenario, moteur);
        runs.put(id, run);
        return info(run);
    }

    /** Cree puis demarre aussitot : c'est ce que fait "lancer une simulation". */
    public RunInfo lancer(Scenario scenario) {
        RunInfo cree = creer(scenario);
        return demarrer(cree.id());
    }

    public RunInfo demarrer(UUID id) {
        Run run = trouver(id);
        synchronized (this) {
            attendu(run, "demarrer", EtatRun.CREE);
            runs.values().stream().filter(r -> r != run && r.etat.actif()).findFirst().ifPresent(r -> {
                throw new IllegalStateException("Un run est deja actif (" + r.id + ") : un seul a la fois");
            });
            run.debut = horlogeReelle.instant();
            changer(run, EtatRun.EN_COURS);
        }
        run.moteur.demarrer();
        return info(run);
    }

    public RunInfo pause(UUID id) {
        Run run = trouver(id);
        synchronized (this) {
            attendu(run, "mettre en pause", EtatRun.EN_COURS);
            changer(run, EtatRun.EN_PAUSE);
        }
        run.moteur.pause();
        return info(run);
    }

    public RunInfo reprendre(UUID id) {
        Run run = trouver(id);
        synchronized (this) {
            attendu(run, "reprendre", EtatRun.EN_PAUSE);
            changer(run, EtatRun.EN_COURS);
        }
        run.moteur.reprendre();
        return info(run);
    }

    /** Arrete proprement : attend la fin du parcours en cours, puis fige l'etat. Un run CREE est simplement annule. */
    public RunInfo arreter(UUID id) {
        Run run = trouver(id);
        synchronized (this) {
            attendu(run, "arreter", EtatRun.CREE, EtatRun.EN_COURS, EtatRun.EN_PAUSE);
            run.fin = horlogeReelle.instant();
            changer(run, EtatRun.ARRETE);
        }
        // Hors du verrou : le fil du moteur peut en avoir besoin pour signaler un echec.
        run.moteur.arreter();
        // ARRETE doit vouloir dire : tout ce qui a ete emis est en base.
        destination.flush();
        return info(run);
    }

    /** Le run demande : compteurs vivants s'il est encore en memoire, sinon ce qu'en garde la base. */
    public RunInfo info(UUID id) {
        Run run = runs.get(id);
        if (run != null) {
            return info(run);
        }
        return store.trouver(id).orElseThrow(() -> new IllegalArgumentException("Run inconnu : " + id));
    }

    /** Tous les runs connus, du plus recent au plus ancien ; les runs en memoire portent leurs compteurs vivants. */
    public List<RunInfo> lister() {
        Map<UUID, RunInfo> parId = new LinkedHashMap<>();
        store.lister().forEach(persiste -> parId.put(persiste.id(), persiste));
        runs.values().forEach(run -> parId.put(run.id, info(run)));
        return List.copyOf(parId.values());
    }

    /** Le run en cours ou en pause, s'il y en a un. */
    public Optional<RunInfo> actif() {
        return runs.values().stream().filter(r -> r.etat.actif()).findFirst().map(this::info);
    }

    @PreDestroy
    void arreterAvantExtinction() {
        actif().ifPresent(r -> arreter(r.id()));
    }

    private synchronized void echec(UUID id, Throwable cause) {
        Run run = runs.get(id);
        LOG.error("Run {} en echec", id, cause);
        if (run != null && run.etat.actif()) {
            run.fin = horlogeReelle.instant();
            run.erreur = cause.getClass().getSimpleName() + " : " + cause.getMessage();
            changer(run, EtatRun.ECHOUE);
        }
    }

    private void changer(Run run, EtatRun nouveau) {
        run.etat = nouveau;
        store.mettreAJour(run.id, nouveau, run.debut, run.fin, run.erreur);
    }

    private static void attendu(Run run, String action, EtatRun... permis) {
        for (EtatRun e : permis) {
            if (run.etat == e) {
                return;
            }
        }
        throw new IllegalStateException("Impossible de " + action + " le run " + run.id + " : il est " + run.etat);
    }

    private Run trouver(UUID id) {
        Run run = runs.get(id);
        if (run == null) {
            throw new IllegalArgumentException("Run inconnu : " + id);
        }
        return run;
    }

    private synchronized RunInfo info(Run run) {
        return new RunInfo(run.id, run.scenario.nom(), run.scenario.seed(), run.etat, run.moteur.parcoursGeneres(),
                run.moteur.evenementsEmis(), run.debut, run.fin, run.erreur);
    }
}

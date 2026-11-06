package ci.cnam.simulator.monitoring;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.monitoring.EtatTableauDeBord.DebitDomaine;
import ci.cnam.simulator.monitoring.EtatTableauDeBord.RunVue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * Construit la photo du simulateur. {@link #echantillonner()} est appele par UN SEUL fil (celui du flux SSE) :
 * les debits sont des differences entre deux echantillons successifs, divisees par le temps ecoule.
 */
@Service
public class TableauDeBordService {

    private final RunManager runs;
    private final ScenarioService scenarios;
    private final MeterRegistry registre;
    private final InfrastructureEtat infrastructure;
    private final Clock horloge;
    private final Map<String, String> domainesParScenario = new ConcurrentHashMap<>();

    private volatile EtatTableauDeBord dernier;

    // Echantillon precedent (touche par le seul fil d'echantillonnage).
    private java.time.Instant precedentInstant;
    private UUID precedentRun;
    private long precedentParcours;
    private long precedentEmis;
    private double precedentEcrits;
    private double precedentLotsMs;
    private long precedentLots;
    private boolean aUnPrecedent;

    public TableauDeBordService(RunManager runs, ScenarioService scenarios, MeterRegistry registre,
                                InfrastructureEtat infrastructure, Clock horloge) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.registre = registre;
        this.infrastructure = infrastructure;
        this.horloge = horloge;
    }

    /** Derniere photo, ou une photo immediate si aucune n'a encore ete prise. */
    public EtatTableauDeBord dernier() {
        EtatTableauDeBord etat = dernier;
        return etat != null ? etat : echantillonner();
    }

    /** Oublie l'echantillon precedent (plus aucun client : la prochaine difference serait trompeuse). */
    public void oublier() {
        aUnPrecedent = false;
    }

    public synchronized EtatTableauDeBord echantillonner() {
        java.time.Instant maintenant = horloge.instant();
        double secondes = aUnPrecedent
                ? Math.max(java.time.Duration.between(precedentInstant, maintenant).toNanos() / 1e9, 1e-3) : 0;

        Optional<RunInfo> actif = runs.actif();
        long parcours = actif.map(RunInfo::parcoursGeneres).orElse(0L);
        long emis = actif.map(RunInfo::evenementsEmis).orElse(0L);
        double parcoursParSeconde = 0;
        double emisParSeconde = 0;
        if (actif.isPresent() && aUnPrecedent && actif.get().id().equals(precedentRun)) {
            parcoursParSeconde = Math.max(0, parcours - precedentParcours) / secondes;
            emisParSeconde = Math.max(0, emis - precedentEmis) / secondes;
        }

        double ecrits = compteur("simulateur.evenements.ecrits");
        double ecritsParSeconde = aUnPrecedent ? Math.max(0, ecrits - precedentEcrits) / secondes : 0;

        Timer lots = registre.find("simulateur.lot.duree").timer();
        long nombreLots = lots == null ? 0 : lots.count();
        double totalLotsMs = lots == null ? 0 : lots.totalTime(TimeUnit.MILLISECONDS);
        double latenceMs = 0;
        if (aUnPrecedent && nombreLots > precedentLots) {
            latenceMs = (totalLotsMs - precedentLotsMs) / (nombreLots - precedentLots);
        } else if (!aUnPrecedent && nombreLots > 0) {
            latenceMs = totalLotsMs / nombreLots;
        }

        InfrastructureEtat.Instantane infra = infrastructure.instantane();
        final double debitParcours = parcoursParSeconde;
        final double debitEmis = emisParSeconde;
        List<DebitDomaine> debits = actif.map(r -> List.of(
                new DebitDomaine(domaine(r.scenario()), debitParcours, debitEmis))).orElse(List.of());

        EtatTableauDeBord etat = new EtatTableauDeBord(horloge.instant(),
                actif.map(r -> new RunVue(r.id(), r.scenario(), r.seed(), r.etat().name(), r.parcoursGeneres(),
                        r.evenementsEmis(), r.erreur())).orElse(null),
                debits, ecritsParSeconde, (long) ecrits,
                (long) compteur("simulateur.lots.echecs"), (long) jauge("simulateur.file.taille"),
                latenceMs, lots == null ? 0 : lots.max(TimeUnit.MILLISECONDS),
                infra.base(), infra.proxy());

        precedentInstant = maintenant;
        precedentRun = actif.map(RunInfo::id).orElse(null);
        precedentParcours = parcours;
        precedentEmis = emis;
        precedentEcrits = ecrits;
        precedentLots = nombreLots;
        precedentLotsMs = totalLotsMs;
        aUnPrecedent = true;
        dernier = etat;
        return etat;
    }

    /** Un seul domaine dans le scenario : son nom ; plusieurs : "tous" (le moteur ne ventile pas par domaine). */
    private String domaine(String nomScenario) {
        return domainesParScenario.computeIfAbsent(nomScenario, nom -> {
            try {
                var noms = scenarios.charger(nom).domaines().keySet();
                return noms.size() == 1 ? noms.iterator().next() : "tous";
            } catch (RuntimeException e) {
                return "inconnu";
            }
        });
    }

    private double compteur(String nom) {
        Counter c = registre.find(nom).counter();
        return c == null ? 0 : c.count();
    }

    private double jauge(String nom) {
        Gauge g = registre.find(nom).gauge();
        return g == null ? 0 : g.value();
    }
}

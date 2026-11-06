package ci.cnam.simulator.web;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.security.AuditService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Actions de l'interface sur les runs et le chaos : traduit un formulaire en appel au moteur (sans le modifier)
 * et inscrit chaque action au journal d'audit. Les droits sont verifies par les controleurs ({@code @PreAuthorize}).
 */
@Service
public class PilotageService {

    private final RunManager runs;
    private final ScenarioService scenarios;
    private final ChaosInjector chaos;
    private final AuditService audit;

    public PilotageService(RunManager runs, ScenarioService scenarios, ChaosInjector chaos, AuditService audit) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.chaos = chaos;
        this.audit = audit;
    }

    /**
     * Lance un scenario ; graine et vitesse du temps remplacent celles du scenario quand elles sont renseignees.
     *
     * @param vitesse secondes simulees par seconde reelle
     */
    public RunInfo lancer(String nomScenario, Long graine, Double vitesse, String acteur) {
        if (nomScenario == null || nomScenario.isBlank()) {
            throw new IllegalArgumentException("Choisir un scenario");
        }
        if (vitesse != null && (vitesse <= 0 || vitesse > 100_000 || vitesse.isNaN())) {
            throw new IllegalArgumentException("Vitesse du temps : nombre strictement positif attendu (100000 au plus)");
        }
        runs.actif().ifPresent(r -> {
            throw new IllegalStateException("Un run est deja actif (" + r.id() + ") : l'arreter avant d'en lancer un autre");
        });
        Scenario base = scenarios.charger(nomScenario);
        Scenario effectif = new Scenario(base.nom(), graine != null ? graine : base.seed(),
                vitesse != null ? vitesse : base.accelerationTemps(), base.debutSimule(), base.domaines(),
                base.phases(), base.contenuYaml());
        RunInfo run = runs.lancer(effectif);
        audit.enregistrer(acteur, "RUN_LANCE", run.id().toString(),
                "scenario " + effectif.nom() + ", graine " + effectif.seed() + ", vitesse x"
                        + effectif.accelerationTemps());
        return run;
    }

    /** Relance le scenario d'un ancien run avec SA graine (memes parametres de scenario, vitesse du scenario). */
    public RunInfo rejouer(UUID runId, String acteur) {
        RunInfo ancien = runs.info(runId);
        RunInfo nouveau = lancer(ancien.scenario(), ancien.seed(), null, acteur);
        audit.enregistrer(acteur, "RUN_REJOUE", nouveau.id().toString(), "rejeu du run " + runId);
        return nouveau;
    }

    public RunInfo pause(UUID id, String acteur) {
        RunInfo run = runs.pause(id);
        audit.enregistrer(acteur, "RUN_PAUSE", id.toString(), null);
        return run;
    }

    public RunInfo reprendre(UUID id, String acteur) {
        RunInfo run = runs.reprendre(id);
        audit.enregistrer(acteur, "RUN_REPRIS", id.toString(), null);
        return run;
    }

    public RunInfo arreter(UUID id, String acteur) {
        RunInfo run = runs.arreter(id);
        audit.enregistrer(acteur, "RUN_ARRETE", id.toString(), null);
        return run;
    }

    public ChaosAction declencher(UUID runId, DemandeChaos demande, String acteur) {
        if (demande.type() == null) {
            throw new IllegalArgumentException("Choisir le type d'action");
        }
        if (demande.dureeSecondes() == null) {
            throw new IllegalArgumentException("La duree est obligatoire");
        }
        Map<String, Object> parametres = new LinkedHashMap<>();
        switch (demande.type()) {
            case COUPURE_BASE -> parametres.put("mode", demande.mode() == null ? "DESACTIVER" : demande.mode());
            case LATENCE -> {
                if (demande.latenceMs() != null) {
                    parametres.put("latenceMs", demande.latenceMs());
                }
                parametres.put("jitterMs", demande.jitterMs() == null ? 0 : demande.jitterMs());
            }
            case SURCHARGE -> {
                if (demande.multiplicateur() != null) {
                    parametres.put("multiplicateur", demande.multiplicateur());
                }
            }
            case MESSAGE_TRONQUE -> {
                if (demande.proportion() != null) {
                    parametres.put("proportion", demande.proportion());
                }
            }
        }
        ChaosAction action = chaos.declencher(runId, demande.type(), Duration.ofSeconds(demande.dureeSecondes()),
                parametres, acteur);
        audit.enregistrer(acteur, "CHAOS_DECLENCHE", runId.toString(),
                demande.type() + " " + demande.dureeSecondes() + " s " + action.parametres());
        return action;
    }

    public List<ChaosAction> retablir(UUID runId, String acteur) {
        List<ChaosAction> annulees = chaos.retablirTout(runId, acteur);
        audit.enregistrer(acteur, "CHAOS_RETABLI", runId.toString(), annulees.size() + " action(s) annulee(s)");
        return annulees;
    }
}

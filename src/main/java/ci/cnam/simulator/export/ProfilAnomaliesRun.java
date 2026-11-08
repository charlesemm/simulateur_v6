package ci.cnam.simulator.export;

import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.core.scenario.ScenarioStore;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Retrouve les taux d'anomalies d'une simulation DEJA TERMINEE, par domaine, en relisant le scenario qui l'a
 * produite. Les deux anomalies de fichier sont posees a l'export, longtemps apres la generation : il faut bien
 * aller rechercher leur dosage la ou il est ecrit.
 *
 * <p>Scenario introuvable ou illisible : aucun taux, donc aucune anomalie de fichier. Un export reste servi,
 * seulement il n'est pas piege -- jamais un export refuse pour cette raison.
 */
@Service
public class ProfilAnomaliesRun implements ProfilsAnomalies {

    private final ScenarioStore scenarios;
    private final ScenarioLoader loader;

    public ProfilAnomaliesRun(ScenarioStore scenarios, ScenarioLoader loader) {
        this.scenarios = scenarios;
        this.loader = loader;
    }

    @Override
    public Map<String, AnomalyProfile> parDomaine(RunInfo run) {
        Map<String, AnomalyProfile> profils = new HashMap<>();
        try {
            Scenario scenario = scenarios.trouverParNom(run.scenario())
                    .map(enregistre -> loader.lire(enregistre.contenuYaml()))
                    .orElse(null);
            if (scenario == null) {
                return Map.of();
            }
            for (Map.Entry<String, ConfigDomaine> domaine : scenario.domaines().entrySet()) {
                profils.put(domaine.getKey(), AnomalyProfile.depuis(domaine.getValue().anomalies()));
            }
        } catch (RuntimeException illisible) {
            return Map.of();
        }
        return profils;
    }
}

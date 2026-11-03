package ci.cnam.simulator.api;

import ci.cnam.simulator.api.dto.LancementRunRequest;
import ci.cnam.simulator.api.dto.RunResponse;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pilotage des runs. Aucun calcul ici : tout est delegue a {@link RunManager} et {@link ScenarioService}. */
@RestController
@RequestMapping("/api/runs")
@Tag(name = "Runs", description = "Lancer, suspendre, reprendre et arreter une simulation")
public class RunController {

    private final RunManager runs;
    private final ScenarioService scenarios;

    public RunController(RunManager runs, ScenarioService scenarios) {
        this.runs = runs;
        this.scenarios = scenarios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lancer une simulation (cree le run puis le demarre)")
    public RunResponse lancer(@Valid @RequestBody LancementRunRequest requete) {
        Scenario scenario = requete.parNom()
                ? scenarios.charger(requete.scenario())
                : scenarios.enregistrer(requete.yaml());
        return RunResponse.de(runs.lancer(scenario));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Mettre le run en pause")
    public RunResponse pause(@PathVariable UUID id) {
        return RunResponse.de(runs.pause(id));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Reprendre un run en pause")
    public RunResponse reprendre(@PathVariable UUID id) {
        return RunResponse.de(runs.reprendre(id));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Arreter le run ; les evenements en attente sont ecrits avant de rendre la main")
    public RunResponse arreter(@PathVariable UUID id) {
        return RunResponse.de(runs.arreter(id));
    }

    @GetMapping
    @Operation(summary = "Lister les runs, du plus recent au plus ancien")
    public List<RunResponse> lister() {
        return runs.lister().stream().map(RunResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Etat d'un run")
    public RunResponse details(@PathVariable UUID id) {
        return RunResponse.de(runs.info(id));
    }
}

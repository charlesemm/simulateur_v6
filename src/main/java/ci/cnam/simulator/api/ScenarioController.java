package ci.cnam.simulator.api;

import ci.cnam.simulator.api.dto.CreationScenarioRequest;
import ci.cnam.simulator.api.dto.ScenarioResponse;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioInconnuException;
import ci.cnam.simulator.core.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catalogue des scenarios : ceux enregistres en base et ceux livres dans {@code scenarios/}. */
@RestController
@RequestMapping("/api/scenarios")
@Tag(name = "Scenarios", description = "Consulter et enregistrer des scenarios")
public class ScenarioController {

    private final ScenarioService scenarios;

    public ScenarioController(ScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    @Operation(summary = "Lister les scenarios disponibles (base et fichiers)")
    public List<ScenarioResponse> lister() {
        return scenarios.lister().stream().map(ScenarioResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Valider et enregistrer un scenario YAML")
    public ScenarioResponse enregistrer(@Valid @RequestBody CreationScenarioRequest requete) {
        Scenario scenario = scenarios.enregistrer(requete.yaml());
        return scenarios.lister().stream()
                .filter(s -> s.nom().equals(scenario.nom()))
                .findFirst()
                .map(ScenarioResponse::de)
                .orElseThrow(() -> new ScenarioInconnuException(scenario.nom()));
    }
}

package ci.cnam.simulator.api;

import ci.cnam.simulator.api.dto.LancementRunRequest;
import ci.cnam.simulator.api.dto.RunResponse;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.security.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pilotage des runs. Aucun calcul ici : tout est delegue a {@link RunManager} et {@link ScenarioService}. */
@RestController
@RequestMapping("/api/runs")
@Validated
@Tag(name = "Runs", description = "Lancer, suspendre, reprendre et arreter une simulation")
public class RunController {

    private final RunManager runs;
    private final ScenarioService scenarios;
    private final AuditService audit;

    public RunController(RunManager runs, ScenarioService scenarios, AuditService audit) {
        this.audit = audit;
        this.runs = runs;
        this.scenarios = scenarios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lancer une simulation (cree le run puis le demarre)")
    public RunResponse lancer(@Valid @RequestBody LancementRunRequest requete, Authentication auth) {
        Scenario scenario = requete.parNom()
                ? scenarios.charger(requete.scenario())
                : scenarios.enregistrer(requete.yaml());
        RunResponse run = RunResponse.de(runs.lancer(scenario));
        audit.enregistrer(acteur(auth), "RUN_LANCE", run.id().toString(), "scenario " + scenario.nom() + ", graine " + scenario.seed());
        return run;
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Mettre le run en pause")
    public RunResponse pause(@PathVariable UUID id, Authentication auth) {
        RunResponse run = RunResponse.de(runs.pause(id));
        audit.enregistrer(acteur(auth), "RUN_PAUSE", id.toString(), null);
        return run;
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Reprendre un run en pause")
    public RunResponse reprendre(@PathVariable UUID id, Authentication auth) {
        RunResponse run = RunResponse.de(runs.reprendre(id));
        audit.enregistrer(acteur(auth), "RUN_REPRIS", id.toString(), null);
        return run;
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Arreter le run ; les evenements en attente sont ecrits avant de rendre la main")
    public RunResponse arreter(@PathVariable UUID id, Authentication auth) {
        RunResponse run = RunResponse.de(runs.arreter(id));
        audit.enregistrer(acteur(auth), "RUN_ARRETE", id.toString(), null);
        return run;
    }

    /**
     * Filtre par statut et limite : c'est ainsi qu'un script demande « la derniere simulation
     * terminee » ({@code ?statut=ARRETE&limit=1}) sans rien savoir d'Echo.
     */
    @GetMapping
    @Operation(summary = "Lister les runs, du plus recent au plus ancien. statut filtre sur l'etat "
            + "(ARRETE pour les simulations terminees), limit borne le nombre rendu")
    public List<RunResponse> lister(
            @Parameter(description = "Ne garder que les runs dans cet etat", example = "ARRETE")
            @RequestParam(required = false) String statut,
            @Parameter(description = "Nombre maximal de runs rendus", example = "1")
            @RequestParam(required = false) @Min(value = 1, message = "limit doit valoir au moins 1")
            @Max(value = 500, message = "limit ne peut depasser 500") Integer limit) {
        EtatRun etat = etat(statut);
        return runs.lister().stream()
                .filter(r -> etat == null || r.etat() == etat)
                .limit(limit == null ? Long.MAX_VALUE : limit)
                .map(RunResponse::de)
                .toList();
    }

    /** @throws IllegalArgumentException statut hors liste : 400, avec les valeurs permises */
    private static EtatRun etat(String statut) {
        if (statut == null || statut.isBlank()) {
            return null;
        }
        try {
            return EtatRun.valueOf(statut.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut inconnu '" + statut + "' (valeurs permises : "
                    + Arrays.toString(EtatRun.values()) + ")");
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Etat d'un run")
    public RunResponse details(@PathVariable UUID id) {
        return RunResponse.de(runs.info(id));
    }

    private static String acteur(Authentication auth) {
        return auth == null ? "inconnu" : auth.getName();
    }
}

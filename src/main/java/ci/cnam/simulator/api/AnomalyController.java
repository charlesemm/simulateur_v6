package ci.cnam.simulator.api;

import ci.cnam.simulator.anomaly.AnomalyQueryService;
import ci.cnam.simulator.api.dto.AnomalieResponse;
import ci.cnam.simulator.api.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lecture de la verite terrain d'un run : les anomalies injectees, avec leur valeur d'origine. */
@RestController
@RequestMapping("/api/runs/{runId}/anomalies")
@Validated
@Tag(name = "Anomalies", description = "Lire la verite terrain des anomalies injectees")
public class AnomalyController {

    private final AnomalyQueryService anomalies;

    public AnomalyController(AnomalyQueryService anomalies) {
        this.anomalies = anomalies;
    }

    @GetMapping
    @Operation(summary = "Anomalies d'un run : filtres type et champ ; pagination bornee")
    public PageResponse<AnomalieResponse> lister(
            @PathVariable UUID runId,
            @Parameter(description = "Type d'anomalie, ex. CHAMP_VIDE")
            @RequestParam(required = false) String type,
            @Parameter(description = "Champ corrompu, ex. nom")
            @RequestParam(required = false) String champ,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page : doit etre positif ou nul") int page,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "taille : au moins 1")
            @Max(value = AnomalyQueryService.TAILLE_PAGE_MAX, message = "taille : 500 au maximum") int taille) {

        AnomalyQueryService.Page resultat = anomalies.rechercher(runId, type, champ, page, taille);
        return new PageResponse<>(
                resultat.contenu().stream().map(AnomalieResponse::de).toList(),
                resultat.page(), resultat.taille(), resultat.total(), resultat.totalPages());
    }
}

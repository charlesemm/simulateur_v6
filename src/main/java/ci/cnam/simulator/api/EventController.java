package ci.cnam.simulator.api;

import ci.cnam.simulator.api.dto.EvenementResponse;
import ci.cnam.simulator.api.dto.PageResponse;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
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

/** Lecture des evenements ecrits par un run : filtres et pagination bornee. */
@RestController
@RequestMapping("/api/runs/{runId}/events")
@Validated
@Tag(name = "Evenements", description = "Lire les evenements ecrits par un run")
public class EventController {

    private final EventQueryService evenements;

    public EventController(EventQueryService evenements) {
        this.evenements = evenements;
    }

    @GetMapping
    @Operation(summary = "Evenements d'un run : filtres domaine, type, statut ; pagination bornee")
    public PageResponse<EvenementResponse> lister(
            @PathVariable UUID runId,
            @Parameter(description = "Domaine emetteur, ex. prestations")
            @RequestParam(required = false) String domaine,
            @Parameter(description = "Type d'evenement, ex. FACTURE_OUVERTE")
            @RequestParam(required = false) String type,
            @Parameter(description = "Statut d'ecriture, ex. ECRIT")
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page : doit etre positif ou nul") int page,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "taille : au moins 1")
            @Max(value = EventQueryService.TAILLE_PAGE_MAX, message = "taille : 500 au maximum") int taille) {

        PageEvenements resultat = evenements.rechercher(runId, domaine, type, statut, page, taille);
        return new PageResponse<>(
                resultat.contenu().stream().map(EvenementResponse::de).toList(),
                resultat.page(), resultat.taille(), resultat.total(), resultat.totalPages());
    }
}

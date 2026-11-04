package ci.cnam.simulator.api;

import ci.cnam.simulator.api.dto.ChaosActionResponse;
import ci.cnam.simulator.api.dto.ChaosRequest;
import ci.cnam.simulator.chaos.ChaosInjector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Crash tests pendant un run. Aucun calcul ici : tout est delegue a {@link ChaosInjector}. */
@RestController
@RequestMapping("/api/runs/{id}/chaos")
@Tag(name = "Chaos", description = "Casser volontairement l'environnement pendant un run, puis tout retablir")
public class ChaosController {

    private final ChaosInjector chaos;

    public ChaosController(ChaosInjector chaos) {
        this.chaos = chaos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Declencher une action de chaos (coupure de la base, latence, surcharge, messages tronques)",
            description = "La duree est obligatoire et plafonnee. L'action prend fin toute seule a l'echeance ou "
                    + "quand le run s'arrete. Une seule action active par type.")
    public ChaosActionResponse declencher(@PathVariable UUID id, @Valid @RequestBody ChaosRequest requete,
                                          Authentication auth) {
        return ChaosActionResponse.de(chaos.declencher(id, requete.type(),
                Duration.ofSeconds(requete.dureeSecondes()), requete.parametres(), auteur(auth)));
    }

    @DeleteMapping
    @Operation(summary = "Tout retablir immediatement (renvoie les actions annulees)")
    public List<ChaosActionResponse> retablirTout(@PathVariable UUID id, Authentication auth) {
        return chaos.retablirTout(id, auteur(auth)).stream().map(ChaosActionResponse::de).toList();
    }

    @GetMapping
    @Operation(summary = "Actions de chaos du run (actives : fin nulle)")
    public List<ChaosActionResponse> lister(@PathVariable UUID id) {
        return chaos.actions(id).stream().map(ChaosActionResponse::de).toList();
    }

    private static String auteur(Authentication auth) {
        return auth == null ? "inconnu" : auth.getName();
    }
}

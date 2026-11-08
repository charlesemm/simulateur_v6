package ci.cnam.simulator.api;

import ci.cnam.simulator.quality.AnalyseAttendue;
import ci.cnam.simulator.quality.AnalyseService;
import ci.cnam.simulator.quality.Echange;
import ci.cnam.simulator.quality.EchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ce qu'Echo attend qu'on analyse, et la trace des echanges. Sous cle d'API : c'est le script
 * d'analyse qui lit ici.
 *
 * <p>Echo n'appelle jamais l'outil et ne connait pas sa machine : il expose, le script prend. Le
 * script reste maitre de son execution — il peut suivre cette liste, ou travailler sans elle.
 *
 * <p>Tant que la planification (prompt 15) n'existe pas, la liste est VIDE : c'est une reponse
 * juste, pas une erreur, et le script n'aura pas a changer quand elle arrivera.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Analyses", description = "Ce qu'Echo attend qu'on analyse, et la trace des echanges")
public class AnalyseController {

    private final AnalyseService analyses;
    private final EchangeService echanges;

    public AnalyseController(AnalyseService analyses, EchangeService echanges) {
        this.analyses = analyses;
        this.echanges = echanges;
    }

    /**
     * @param planifiee faux tant que la planification n'est pas branchee : la liste vide n'est alors
     *                  pas un « rien a faire », c'est un « Echo ne planifie encore rien »
     */
    public record AnalysesAttenduesResponse(boolean planifiee, int nombre, List<AnalyseAttendue> analyses) {
    }

    @GetMapping("/analyses/attendues")
    @Operation(summary = "Les analyses attendues maintenant. Liste vide tant que la planification "
            + "(prompt 15) n'est pas branchee : planifiee vaut alors false")
    public AnalysesAttenduesResponse attendues() {
        List<AnalyseAttendue> attendues = analyses.attendues();
        return new AnalysesAttenduesResponse(!analyses.planificationAbsente(), attendues.size(), attendues);
    }

    @GetMapping("/runs/{runId}/echanges")
    @Operation(summary = "Journal des echanges de cette simulation, du plus ancien au plus recent, "
            + "avec l'etat qui en decoule (un echec d'echange ne produit aucun taux)")
    public JournalEchanges echanges(@PathVariable UUID runId) {
        return new JournalEchanges(runId,
                echanges.etat(runId).map(e -> new Etat(e.statut().name(), e.libelle(), e.echec(), e.mesurable(),
                        e.detail())).orElse(null),
                echanges.journal(runId));
    }

    /** @param etat ou en est l'echange, nul si aucun echange n'a eu lieu pour cette simulation */
    public record JournalEchanges(UUID runId, Etat etat, List<Echange> echanges) {
    }

    /** @param mesurable faux tant qu'aucun rapport exploitable n'est arrive : aucun taux n'est rendu */
    public record Etat(String statut, String libelle, boolean echec, boolean mesurable, String detail) {
    }
}

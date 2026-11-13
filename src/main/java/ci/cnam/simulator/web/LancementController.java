package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.ScenarioService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Lancement et pilotage d'un run. Les boutons sont masques a l'OBSERVATEUR dans la page ET refuses par le serveur
 * ({@code @PreAuthorize}). Aucune logique ici : {@link PilotageService} fait le travail.
 */
@Controller
@PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
public class LancementController {

    private static final String ZONE = "lancement :: zone";

    private final PilotageService pilotage;
    private final RunManager runs;
    private final ScenarioService scenarios;

    public LancementController(PilotageService pilotage, RunManager runs, ScenarioService scenarios) {
        this.pilotage = pilotage;
        this.runs = runs;
        this.scenarios = scenarios;
    }

    @GetMapping("/lancement")
    @PreAuthorize("isAuthenticated()")
    public String page(@RequestParam(required = false) String scenario, Model modele) {
        preparer(modele);
        // Arrivee depuis la liste des scenarios : celui qu'on vient de choisir est deja selectionne.
        modele.addAttribute("choisi", scenario);
        return "lancement";
    }

    @GetMapping("/lancement/zone")
    @PreAuthorize("isAuthenticated()")
    public String zone(Model modele) {
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/lancement")
    public String lancer(@RequestParam String scenario, @RequestParam(required = false) Long graine,
                         @RequestParam(required = false) Double vitesse, Authentication auth, Model modele,
                         HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            var run = pilotage.lancer(scenario, graine, vitesse, auth.getName());
            return "Simulation démarrée : scénario « " + run.scenario() + " » lancé (graine " + run.seed() + ").";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/lancement/{id}/pause")
    public String pause(@PathVariable UUID id, Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            pilotage.pause(id, auth.getName());
            return "Simulation mise en pause.";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/lancement/{id}/reprendre")
    public String reprendre(@PathVariable UUID id, Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            pilotage.reprendre(id, auth.getName());
            return "Simulation reprise.";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/lancement/{id}/arreter")
    public String arreter(@PathVariable UUID id, Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            pilotage.arreter(id, auth.getName());
            return "Simulation arrêtée : les événements en attente ont été enregistrés.";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/lancement/{id}/rejouer")
    public String rejouer(@PathVariable UUID id, Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            var run = pilotage.rejouer(id, auth.getName());
            return "Simulation rejouée : scénario « " + run.scenario() + " » lancé (graine " + run.seed() + "), comme la simulation " + id.toString().substring(0, 8) + ".";
        });
        preparer(modele);
        return ZONE;
    }

    private void preparer(Model modele) {
        modele.addAttribute("scenarios", scenarios.lister());
        modele.addAttribute("actif", runs.actif().orElse(null));
        modele.addAttribute("derniere", runs.lister().stream().findFirst().orElse(null));
    }
}

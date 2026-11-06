package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Historique des runs, du plus recent au plus ancien. Le rejeu passe par {@link LancementController}. */
@Controller
public class HistoriqueController {

    private final RunManager runs;

    public HistoriqueController(RunManager runs) {
        this.runs = runs;
    }

    @GetMapping("/historique")
    public String page(Model modele) {
        modele.addAttribute("runs", runs.lister());
        return "history";
    }
}

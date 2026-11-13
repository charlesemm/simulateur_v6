package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tableau de bord. Les mesures en direct arrivent par le flux SSE ({@code /flux}) ; la page porte en plus
 * les dernieres simulations, pour qu'on sache en arrivant ou en est le travail et ou aller ensuite.
 */
@Controller
public class TableauDeBordController {

    /** Nombre de simulations rappelees sur le tableau de bord ; la liste complete est sur son ecran. */
    static final int RECENTES = 5;

    private final RunManager runs;

    public TableauDeBordController(RunManager runs) {
        this.runs = runs;
    }

    @GetMapping("/")
    public String racine() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String tableauDeBord(Model modele) {
        List<RunInfo> toutes = runs.lister();
        modele.addAttribute("recentes", toutes.stream().limit(RECENTES).toList());
        modele.addAttribute("total", toutes.size());
        return "dashboard";
    }
}

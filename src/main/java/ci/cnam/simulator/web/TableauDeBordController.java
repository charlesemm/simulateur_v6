package ci.cnam.simulator.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Tableau de bord : la page est statique, les valeurs arrivent par le flux SSE ({@code /flux}). */
@Controller
public class TableauDeBordController {

    @GetMapping("/")
    public String racine() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String tableauDeBord() {
        return "dashboard";
    }
}

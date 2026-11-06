package ci.cnam.simulator.web;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.chaos.LoadMultiplier;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Panneau chaos. La lecture est ouverte a tout compte connecte ; declencher et "tout retablir" sont reserves a
 * ADMIN et OPERATEUR (les boutons sont aussi masques aux autres, mais c'est ce controle serveur qui compte).
 */
@Controller
public class ChaosPanelController {

    private static final String ZONE = "chaos-panel :: zone";

    private final PilotageService pilotage;
    private final ChaosInjector chaos;
    private final RunManager runs;

    public ChaosPanelController(PilotageService pilotage, ChaosInjector chaos, RunManager runs) {
        this.pilotage = pilotage;
        this.chaos = chaos;
        this.runs = runs;
    }

    @GetMapping("/chaos")
    public String page(Model modele) {
        preparer(modele);
        return "chaos-panel";
    }

    @GetMapping("/chaos/zone")
    public String zone(Model modele) {
        preparer(modele);
        return ZONE;
    }

    /** Rafraichi par htmx toutes les 3 s : seulement la liste des actions, pour ne pas effacer un formulaire en cours. */
    @GetMapping("/chaos/actions")
    public String actions(Model modele) {
        preparer(modele);
        return "chaos-panel :: actions";
    }

    @PostMapping("/chaos/declencher")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String declencher(DemandeChaos demande, Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            RunInfo run = runs.actif().orElseThrow(() ->
                    new IllegalStateException("Aucun run actif : lancer une simulation avant de casser quoi que ce soit"));
            ChaosAction action = pilotage.declencher(run.id(), demande, auth.getName());
            return "Action " + action.type() + " démarrée pour " + demande.dureeSecondes() + " s";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/chaos/retablir")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String retablir(Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            RunInfo run = derniereCible().orElseThrow(() -> new IllegalStateException("Aucun run connu"));
            int n = pilotage.retablir(run.id(), auth.getName()).size();
            return n + " action(s) annulée(s) : tout est rétabli";
        });
        preparer(modele);
        return ZONE;
    }

    private void preparer(Model modele) {
        RunInfo actif = runs.actif().orElse(null);
        // "Tout retablir" doit marcher meme si le run vient de s'arreter : on retombe sur le run le plus recent.
        RunInfo cible = actif != null ? actif : derniereCible().orElse(null);
        List<ChaosAction> actions = cible == null ? List.of() : chaos.actions(cible.id());
        modele.addAttribute("actif", actif);
        modele.addAttribute("cible", cible);
        modele.addAttribute("actions", reverse(actions));
        modele.addAttribute("paliers", new TreeSet<>(LoadMultiplier.PALIERS));
        modele.addAttribute("dureeMax", chaos.dureeMax().toSeconds());
    }

    private java.util.Optional<RunInfo> derniereCible() {
        return runs.actif().or(() -> runs.lister().stream().findFirst());
    }

    private static <T> List<T> reverse(List<T> liste) {
        List<T> copie = new java.util.ArrayList<>(liste);
        Collections.reverse(copie);
        return copie;
    }
}

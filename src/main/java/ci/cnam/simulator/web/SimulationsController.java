package ci.cnam.simulator.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Les simulations : la liste (ancien « historique », dont l'adresse reste servie) et la fiche de chacune.
 * La fiche est le point d'entree vers ses trois autres onglets : flux de donnees, analyse, export.
 * Le rejeu passe par {@link LancementController}. Aucune logique : {@link SimulationWebService} assemble.
 */
@Controller
public class SimulationsController {

    private final SimulationWebService simulations;

    public SimulationsController(SimulationWebService simulations) {
        this.simulations = simulations;
    }

    @GetMapping({"/simulations", "/historique"})
    public String liste(Model modele) {
        modele.addAttribute("runs", simulations.toutes());
        return "simulations";
    }

    @GetMapping("/simulations/{id}")
    public String fiche(@PathVariable UUID id, Model modele) {
        var fiche = simulations.fiche(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation inconnue : " + id));
        modele.addAttribute("fiche", fiche);
        modele.addAttribute("courant", fiche.run());
        return "simulation";
    }
}

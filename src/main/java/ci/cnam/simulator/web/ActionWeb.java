package ci.cnam.simulator.web;

import ci.cnam.simulator.chaos.ChaosIndisponibleException;
import ci.cnam.simulator.core.scenario.ScenarioInconnuException;
import ci.cnam.simulator.core.scenario.ScenarioInvalideException;
import ci.cnam.simulator.security.ComptesService.ComptesException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.ui.Model;

/**
 * Execute une action de l'interface et traduit les refus metier en message affichable : statut 422 (que htmx
 * accepte d'afficher, voir app.js) et attribut {@code erreur}. Une action reussie peut rendre un attribut {@code message}.
 */
final class ActionWeb {

    private ActionWeb() {
    }

    static void executer(Model modele, HttpServletResponse reponse, Supplier<String> action) {
        try {
            String message = action.get();
            if (message != null) {
                modele.addAttribute("message", message);
            }
        } catch (ScenarioInvalideException e) {
            refuser(modele, reponse, "Scénario invalide : " + String.join(" ; ", e.erreurs()));
        } catch (ScenarioInconnuException | ChaosIndisponibleException | ComptesException
                 | IllegalArgumentException | IllegalStateException e) {
            refuser(modele, reponse, e.getMessage());
        }
    }

    private static void refuser(Model modele, HttpServletResponse reponse, String message) {
        reponse.setStatus(422);
        modele.addAttribute("erreur", message);
    }
}

package ci.cnam.simulator.web;

import ci.cnam.simulator.core.scenario.ScenarioEnregistre;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Editeur de scenarios. Lire, generer, valider et exporter sont ouverts a tout compte connecte (rien n'est
 * modifie) ; enregistrer est reserve a ADMIN et OPERATEUR.
 */
@Controller
public class EditeurScenarioController {

    private static final String ZONE = "scenario-editor :: zone";

    private final EditeurScenarioService editeur;

    public EditeurScenarioController(EditeurScenarioService editeur) {
        this.editeur = editeur;
    }

    @GetMapping("/scenarios")
    public String page(Model modele) {
        modele.addAttribute("scenarios", editeur.lister());
        return "scenario-editor";
    }

    /** Formulaire vers YAML : remplit la zone de texte, puis la valide. */
    @PostMapping("/scenarios/generer")
    public String generer(FormulaireScenario formulaire, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            String yaml = editeur.genererYaml(formulaire);
            modele.addAttribute("yaml", yaml);
            appliquerValidation(modele, yaml);
            return null;
        });
        return ZONE;
    }

    @PostMapping("/scenarios/valider")
    public String valider(@RequestParam String yaml, Model modele) {
        modele.addAttribute("yaml", yaml);
        appliquerValidation(modele, yaml);
        return ZONE;
    }

    @PostMapping("/scenarios/enregistrer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String enregistrer(@RequestParam String yaml, Authentication auth, Model modele,
                              HttpServletResponse reponse) {
        modele.addAttribute("yaml", yaml);
        ActionWeb.executer(modele, reponse, () -> {
            var scenario = editeur.enregistrer(yaml, auth.getName());
            return "Scénario '" + scenario.nom() + "' enregistré";
        });
        return ZONE;
    }

    /** Charge un scenario existant dans l'editeur. */
    @GetMapping("/scenarios/charger")
    public String charger(@RequestParam String nom, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            String yaml = editeur.trouver(nom).contenuYaml();
            modele.addAttribute("yaml", yaml);
            appliquerValidation(modele, yaml);
            return null;
        });
        return ZONE;
    }

    /** Import : le fichier remplit l'editeur et est valide ; rien n'est enregistre tant qu'on ne le demande pas. */
    @PostMapping("/scenarios/importer")
    public String importer(@RequestParam MultipartFile fichier, Model modele, HttpServletResponse reponse)
            throws IOException {
        if (fichier.isEmpty()) {
            reponse.setStatus(422);
            modele.addAttribute("erreur", "Aucun fichier recu");
        } else if (fichier.getSize() > EditeurScenarioService.TAILLE_YAML_MAX) {
            reponse.setStatus(422);
            modele.addAttribute("erreur", "Fichier trop volumineux (" + EditeurScenarioService.TAILLE_YAML_MAX
                    + " octets au plus)");
        } else {
            String yaml = new String(fichier.getBytes(), StandardCharsets.UTF_8);
            modele.addAttribute("yaml", yaml);
            appliquerValidation(modele, yaml);
        }
        return ZONE;
    }

    @GetMapping("/scenarios/export")
    public ResponseEntity<byte[]> exporter(@RequestParam String nom) {
        ScenarioEnregistre scenario = editeur.trouver(nom);
        String fichier = scenario.nom().replaceAll("[^A-Za-z0-9._-]", "_") + ".yaml";
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "x-yaml", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fichier).build().toString())
                .body(scenario.contenuYaml().getBytes(StandardCharsets.UTF_8));
    }

    private void appliquerValidation(Model modele, String yaml) {
        var validation = editeur.valider(yaml);
        modele.addAttribute("erreurs", validation.erreurs());
        modele.addAttribute("resume", validation.resume());
    }
}

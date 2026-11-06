package ci.cnam.simulator.web;

import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.security.AuditService;
import ci.cnam.simulator.security.ComptesService;
import ci.cnam.simulator.security.Role;
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

/** Administration des comptes et lecture du journal d'audit : ADMIN seulement (verifie cote serveur). */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminUtilisateursController {

    private static final String ZONE = "admin-users :: zone";

    private final ComptesService comptes;
    private final AuditService audit;
    private final java.time.Clock horloge;

    public AdminUtilisateursController(ComptesService comptes, AuditService audit, java.time.Clock horloge) {
        this.horloge = horloge;
        this.comptes = comptes;
        this.audit = audit;
    }

    @GetMapping("/admin/utilisateurs")
    public String page(Model modele) {
        preparer(modele);
        return "admin-users";
    }

    @PostMapping("/admin/utilisateurs")
    public String creer(@RequestParam String nom, @RequestParam String motDePasse, @RequestParam Role role,
                        Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            AppUser cree = comptes.creer(auth.getName(), nom, motDePasse, role);
            return "Compte '" + cree.getNomUtilisateur() + "' créé : mot de passe à changer à la première connexion";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/admin/utilisateurs/{id}/role")
    public String changerRole(@PathVariable UUID id, @RequestParam Role role, Authentication auth, Model modele,
                              HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            comptes.changerRole(auth.getName(), id, role);
            return "Rôle modifié";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/admin/utilisateurs/{id}/actif")
    public String basculerActif(@PathVariable UUID id, Authentication auth, Model modele,
                                HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            comptes.basculerActif(auth.getName(), id);
            return "Compte mis à jour";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/admin/utilisateurs/{id}/deverrouiller")
    public String deverrouiller(@PathVariable UUID id, Authentication auth, Model modele,
                                HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            comptes.deverrouiller(auth.getName(), id);
            return "Compte déverrouillé";
        });
        preparer(modele);
        return ZONE;
    }

    @PostMapping("/admin/utilisateurs/{id}/mot-de-passe")
    public String reinitialiser(@PathVariable UUID id, @RequestParam String motDePasse, Authentication auth,
                                Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            comptes.reinitialiserMotDePasse(auth.getName(), id, motDePasse);
            return "Mot de passe temporaire posé : le titulaire devra le changer";
        });
        preparer(modele);
        return ZONE;
    }

    private void preparer(Model modele) {
        modele.addAttribute("utilisateurs", comptes.lister());
        modele.addAttribute("roles", Role.values());
        modele.addAttribute("journal", audit.derniers());
        modele.addAttribute("maintenant", horloge.instant());
    }
}

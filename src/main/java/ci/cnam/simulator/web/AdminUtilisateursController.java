package ci.cnam.simulator.web;

import ci.cnam.simulator.security.AuditService;
import ci.cnam.simulator.security.CompteCree;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
    public String creer(@RequestParam String nom, @RequestParam(required = false) String email,
                        @RequestParam(required = false) String motDePasse, @RequestParam Role role,
                        Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            CompteCree cree = comptes.creer(auth.getName(), nom, email, motDePasse, role);
            remettre(modele, cree);
            return "Compte « " + cree.compte().getNomUtilisateur() + " » créé.";
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

    @PostMapping("/admin/utilisateurs/{id}/email")
    public String changerEmail(@PathVariable UUID id, @RequestParam(required = false) String email,
                               Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            comptes.changerEmail(auth.getName(), id, email);
            return "Adresse enregistrée";
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
    public String reinitialiser(@PathVariable UUID id, @RequestParam(required = false) String motDePasse,
                                Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            CompteCree remis = comptes.reinitialiserMotDePasse(auth.getName(), id, motDePasse);
            remettre(modele, remis);
            return "Nouveau mot de passe temporaire posé.";
        });
        preparer(modele);
        return ZONE;
    }

    /**
     * Prepare le bloc « remise des identifiants » affiche UNE seule fois.
     *
     * <p>Le mot de passe en clair ne sort que dans cette reponse HTML : il n'est ni enregistre, ni journalise,
     * ni renvoye par aucun autre ecran. L'adresse du simulateur est deduite de la requete courante pour que le
     * message prepare contienne un lien cliquable, meme derriere le reverse proxy.</p>
     */
    private void remettre(Model modele, CompteCree cree) {
        modele.addAttribute("identifiants", cree);
        modele.addAttribute("adresseConnexion", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/login").build().toUriString());
    }

    private void preparer(Model modele) {
        modele.addAttribute("utilisateurs", comptes.lister());
        modele.addAttribute("roles", Role.values());
        modele.addAttribute("journal", audit.derniers());
        modele.addAttribute("maintenant", horloge.instant());
    }
}

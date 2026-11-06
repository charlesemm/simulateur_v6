package ci.cnam.simulator.web;

import ci.cnam.simulator.security.AppUserPrincipal;
import ci.cnam.simulator.security.ComptesService;
import ci.cnam.simulator.security.ComptesService.ComptesException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Connexion et changement de mot de passe. Aucune regle ici : tout est delegue a {@link ComptesService}. */
@Controller
public class LoginController {

    private final ComptesService comptes;
    private final SecurityContextRepository contexte = new HttpSessionSecurityContextRepository();

    public LoginController(ComptesService comptes) {
        this.comptes = comptes;
    }

    @GetMapping("/login")
    public String connexion() {
        return "login";
    }

    @GetMapping("/mot-de-passe")
    public String formulaireMotDePasse(Authentication auth, Model modele) {
        modele.addAttribute("obligatoire", auth.getPrincipal() instanceof AppUserPrincipal p
                && p.motDePasseAChanger());
        return "mot-de-passe";
    }

    @PostMapping("/mot-de-passe")
    public String changerMotDePasse(@RequestParam String ancien, @RequestParam String nouveau,
                                    @RequestParam String confirmation, Authentication auth,
                                    HttpServletRequest requete, HttpServletResponse reponse, Model modele) {
        try {
            if (!nouveau.equals(confirmation)) {
                throw new ComptesException("La confirmation ne correspond pas au nouveau mot de passe");
            }
            String nouveauHash = comptes.changerMotDePasse(auth.getName(), ancien, nouveau);
            rafraichirSession(auth, nouveauHash, requete, reponse);
            return "redirect:/";
        } catch (ComptesException e) {
            modele.addAttribute("erreur", e.getMessage());
            modele.addAttribute("obligatoire", auth.getPrincipal() instanceof AppUserPrincipal p
                    && p.motDePasseAChanger());
            return "mot-de-passe";
        }
    }

    /** La session garde l'ancien indicateur "mot de passe a changer" : on la remplace par un compte a jour. */
    private void rafraichirSession(Authentication auth, String nouveauHash, HttpServletRequest requete,
                                   HttpServletResponse reponse) {
        if (auth.getPrincipal() instanceof AppUserPrincipal p) {
            AppUserPrincipal aJour = p.motDePasseChange(nouveauHash);
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(aJour, null,
                    aJour.getAuthorities()));
            SecurityContextHolder.setContext(ctx);
            contexte.saveContext(ctx, requete, reponse);
        }
    }
}

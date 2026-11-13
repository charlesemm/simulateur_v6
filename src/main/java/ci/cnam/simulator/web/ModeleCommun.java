package ci.cnam.simulator.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Ce que tous les ecrans ont en commun : les libelles ({@link Libelles}) et le role du compte connecte,
 * dit en clair dans l'en-tete. Limite aux controleurs de l'interface ; l'API n'est pas concernee.
 */
@ControllerAdvice(basePackageClasses = ModeleCommun.class)
public class ModeleCommun {

    @ModelAttribute("libelles")
    public Libelles libelles() {
        return Libelles.INSTANCE;
    }

    /** Code du role courant ({@code ADMIN}, {@code OPERATEUR}, {@code OBSERVATEUR}), vide sans connexion. */
    @ModelAttribute("roleCourant")
    public String roleCourant(Authentication auth) {
        if (auth == null) {
            return "";
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse("");
    }
}

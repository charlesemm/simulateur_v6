package ci.cnam.simulator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tant que le titulaire n'a pas remplace son mot de passe temporaire, toute page (sauf le changement de mot de passe,
 * la deconnexion et les ressources statiques) renvoie vers l'ecran de changement. Ce n'est PAS un bean : il est
 * instancie par {@link SecurityConfig} pour ne pas etre enregistre sur toutes les requetes par Boot.
 */
public class MotDePasseAChangerFilter extends OncePerRequestFilter {

    public static final String PAGE = "/mot-de-passe";

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.motDePasseAChanger()
                && !exempte(requete.getRequestURI().substring(requete.getContextPath().length()))) {
            reponse.sendRedirect(requete.getContextPath() + PAGE);
            return;
        }
        suite.doFilter(requete, reponse);
    }

    private static boolean exempte(String chemin) {
        return chemin.equals(PAGE) || chemin.equals("/logout") || chemin.equals("/login") || chemin.equals("/error")
                || chemin.startsWith("/css/") || chemin.startsWith("/js/");
    }
}

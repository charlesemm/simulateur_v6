package ci.cnam.simulator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentification des appels {@code /api/**} par cle d'API.
 *
 * <p>La cle attendue vient de la variable d'environnement {@code API_KEY}
 * (propriete {@code simulateur.securite.cle-api}). Elle est lue dans l'en-tete
 * {@code X-API-Key} et comparee a temps constant. Sans cle ou avec une mauvaise cle :
 * 401 au format d'erreur de l'API. Les roles arrivent a la brique "Securite".
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    /** En-tete porteur de la cle. */
    public static final String ENTETE = "X-API-Key";

    private static final String CORPS_401 =
            "{\"code\":\"CLE_API_INVALIDE\",\"message\":\"Cle d'API absente ou invalide (en-tete X-API-Key)\"}";

    private final byte[] cleAttendue;

    public ApiKeyFilter(String cleAttendue) {
        this.cleAttendue = cleAttendue == null ? new byte[0] : cleAttendue.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {

        String fournie = requete.getHeader(ENTETE);
        if (fournie == null || cleAttendue.length == 0
                || !MessageDigest.isEqual(cleAttendue, fournie.getBytes(StandardCharsets.UTF_8))) {
            reponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            reponse.setHeader(HttpHeaders.WWW_AUTHENTICATE, ENTETE);
            reponse.getWriter().write(CORPS_401);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "api", null, List.of(new SimpleGrantedAuthority("ROLE_API"))));
        try {
            suite.doFilter(requete, reponse);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

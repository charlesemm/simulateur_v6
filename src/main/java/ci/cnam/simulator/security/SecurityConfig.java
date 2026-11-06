package ci.cnam.simulator.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Deux chaines separees, comme prevu au cahier des charges :
 * <ul>
 *   <li>{@code /api/**} : cle d'API ({@link ApiKeyFilter}), sans session, CSRF desactive ;</li>
 *   <li>le reste : formulaire de connexion, session, CSRF actif, roles {@link Role} verifies cote serveur
 *       ({@code @PreAuthorize}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Documentation : ouverte aux comptes connectes seulement (la cle d'API se saisit dans Swagger). */
    private static final String[] DOCUMENTATION = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    /** Ressources servies localement, necessaires a la page de connexion elle-meme. */
    /** Styles, scripts et polices doivent etre lisibles avant la connexion : la page /login s'en sert. */
    private static final String[] RESSOURCES_PUBLIQUES = {"/css/**", "/js/**", "/fonts/**", "/error"};

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ApiKeyFilter apiKeyFilter(ApiKeyService cles) {
        return new ApiKeyFilter(cles::valide);
    }

    /**
     * Un bean de type {@code Filter} est sinon enregistre par Boot sur TOUTES les requetes :
     * on le desactive au niveau du conteneur, il ne sert que dans la chaine {@code /api/**}.
     */
    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilterNonGlobal(ApiKeyFilter filtre) {
        FilterRegistrationBean<ApiKeyFilter> enregistrement = new FilterRegistrationBean<>(filtre);
        enregistrement.setEnabled(false);
        return enregistrement;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain apiFilterChain(HttpSecurity http, ApiKeyFilter apiKeyFilter) throws Exception {
        http
                .securityMatcher("/api/**")
                // CSRF desactive VOLONTAIREMENT sur cette chaine, et sur elle seule :
                // elle est STATELESS (aucune session, aucun cookie, ni formulaire ni Basic) et
                // s'authentifie par le seul en-tete X-API-Key, qu'un navigateur n'ajoute jamais de
                // lui-meme a une requete declenchee par un site tiers. Il n'y a donc pas
                // d'authentification ambiante a detourner. CSRF reste actif sur l'interface web.
                // codeql[java/spring-disabled-csrf-protection]
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    SecurityFilterChain interfaceFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(RESSOURCES_PUBLIQUES).permitAll()
                        .requestMatchers(DOCUMENTATION).authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        // Message identique pour mauvais mot de passe, compte inconnu, verrouille ou desactive.
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("/login?error"))
                        .defaultSuccessUrl("/", false))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .addFilterAfter(new MotDePasseAChangerFilter(), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}

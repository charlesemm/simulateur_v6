package ci.cnam.simulator.security;

import ci.cnam.simulator.SimulateurProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Deux chaines separees, comme prevu au cahier des charges :
 * <ul>
 *   <li>{@code /api/**} : cle d'API ({@link ApiKeyFilter}), sans session, CSRF desactive ;</li>
 *   <li>le reste : formulaire de connexion, session, CSRF actif.</li>
 * </ul>
 * Les roles utilisateur ({@code ADMIN}, {@code OPERATEUR}, {@code OBSERVATEUR}) et les comptes
 * en base viennent a la brique "Securite et interface".
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Chemins de documentation, laisses ouverts pour que Swagger puisse s'afficher et tester. */
    private static final String[] DOCUMENTATION = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    @Bean
    ApiKeyFilter apiKeyFilter(SimulateurProperties proprietes) {
        return new ApiKeyFilter(proprietes.securite().cleApi());
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
                        .requestMatchers(DOCUMENTATION).permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}

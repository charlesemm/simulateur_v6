package ci.cnam.simulator.api;

import ci.cnam.simulator.security.ApiKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description OpenAPI et declaration du schema de cle d'API, pour pouvoir
 * s'authentifier depuis Swagger (bouton « Authorize », en-tete X-API-Key).
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEMA = "cleApi";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Simulateur de flux de donnees CNAM")
                        .version("v1")
                        .description("""
                                Donnees entierement synthetiques : aucune donnee reelle.
                                Toutes les routes /api/** exigent l'en-tete X-API-Key."""))
                .components(new Components().addSecuritySchemes(SCHEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyFilter.ENTETE)
                        .description("Cle d'API, variable d'environnement API_KEY")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA));
    }
}

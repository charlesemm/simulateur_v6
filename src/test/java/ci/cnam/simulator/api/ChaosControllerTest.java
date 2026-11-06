package ci.cnam.simulator.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosIndisponibleException;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.chaos.ChaosType;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.SecurityConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Routes de chaos : cle d'API, validation, codes d'erreur stables. Les regles metier sont testees dans ChaosInjectorTest. */
@WebMvcTest(controllers = ChaosController.class, properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, ApiExceptionHandler.class, ChaosControllerTest.CleDeTest.class})
@EnableAutoConfiguration
class ChaosControllerTest {

    private static final String CLE = "test-api-key";

    @TestConfiguration
    static class CleDeTest {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter(CLE);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean ChaosInjector chaos;
    @MockitoBean ci.cnam.simulator.security.AuditService audit;

    private static ChaosAction action(UUID run, ChaosType type) {
        Instant debut = Instant.parse("2026-01-01T08:00:00Z");
        return new ChaosAction(UUID.randomUUID(), run, type, Map.of("mode", "DESACTIVER"), debut,
                debut.plusSeconds(30), null, null, "api", 0);
    }

    @Test
    @DisplayName("POST /chaos declenche l'action, rend 201, et l'auteur est celui de l'appelant authentifie")
    void declencher() throws Exception {
        UUID run = UUID.randomUUID();
        when(chaos.declencher(eq(run), eq(ChaosType.COUPURE_BASE), eq(Duration.ofSeconds(30)), any(), eq("api")))
                .thenReturn(action(run, ChaosType.COUPURE_BASE));

        mvc.perform(post("/api/runs/" + run + "/chaos").header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"COUPURE_BASE\",\"dureeSecondes\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COUPURE_BASE"))
                .andExpect(jsonPath("$.fin").doesNotExist())
                .andExpect(jsonPath("$.auteur").value("api"));
    }

    @Test
    @DisplayName("Sans cle d'API : 401, rien n'est declenche")
    void sansCle() throws Exception {
        mvc.perform(post("/api/runs/" + UUID.randomUUID() + "/chaos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"COUPURE_BASE\",\"dureeSecondes\":30}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/runs/" + UUID.randomUUID() + "/chaos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Type inconnu, duree absente ou negative : 400 REQUETE_INVALIDE")
    void requeteInvalide() throws Exception {
        String url = "/api/runs/" + UUID.randomUUID() + "/chaos";
        for (String corps : List.of("{\"type\":\"EXPLOSION\",\"dureeSecondes\":30}", "{\"type\":\"LATENCE\"}",
                "{\"type\":\"LATENCE\",\"dureeSecondes\":-5}")) {
            mvc.perform(post(url).header(ApiKeyFilter.ENTETE, CLE).contentType(MediaType.APPLICATION_JSON)
                            .content(corps))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        }
    }

    @Test
    @DisplayName("Duree au-dela du plafond : 400 ; run inactif ou action deja en cours : 409 ; Toxiproxy absent : 503")
    void erreursMetier() throws Exception {
        UUID run = UUID.randomUUID();
        String url = "/api/runs/" + run + "/chaos";
        String corps = "{\"type\":\"COUPURE_BASE\",\"dureeSecondes\":30}";
        when(chaos.declencher(eq(run), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("duree : 999 s depasse la duree maximale de 300 s"))
                .thenThrow(new IllegalStateException("Une action COUPURE_BASE est deja en cours"))
                .thenThrow(new ChaosIndisponibleException("Toxiproxy injoignable"));

        mvc.perform(post(url).header(ApiKeyFilter.ENTETE, CLE).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        mvc.perform(post(url).header(ApiKeyFilter.ENTETE, CLE).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAT_IMPOSSIBLE"));
        mvc.perform(post(url).header(ApiKeyFilter.ENTETE, CLE).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CHAOS_INDISPONIBLE"));
    }

    @Test
    @DisplayName("Run inconnu : 404 RUN_INCONNU")
    void runInconnu() throws Exception {
        UUID run = UUID.randomUUID();
        when(chaos.retablirTout(eq(run), any())).thenThrow(new IllegalArgumentException("Run inconnu : " + run));

        mvc.perform(delete("/api/runs/" + run + "/chaos").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_INCONNU"));
    }

    @Test
    @DisplayName("DELETE /chaos retablit tout et rend les actions annulees ; GET liste les actions")
    void retablirEtLister() throws Exception {
        UUID run = UUID.randomUUID();
        ChaosAction a = action(run, ChaosType.LATENCE).terminee(Instant.parse("2026-01-01T08:00:10Z"), "RETABLIE", 0);
        when(chaos.retablirTout(eq(run), eq("api"))).thenReturn(List.of(a));
        when(chaos.actions(run)).thenReturn(List.of(a));

        mvc.perform(delete("/api/runs/" + run + "/chaos").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].motifFin").value("RETABLIE"))
                .andExpect(jsonPath("$[0].fin").exists());
        verify(chaos).retablirTout(run, "api");
        mvc.perform(get("/api/runs/" + run + "/chaos").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LATENCE"));
    }
}

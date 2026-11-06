package ci.cnam.simulator.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.EvenementLu;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioInvalideException;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalDateTime;
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

/**
 * Couche web seule : les services sont simules. Verifie les routes, la cle d'API,
 * le format des erreurs, la pagination et les filtres.
 */
@WebMvcTest(controllers = {RunController.class, EventController.class, ScenarioController.class},
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, ApiExceptionHandler.class, ApiMockMvcTest.CleDeTest.class})
@EnableAutoConfiguration
class ApiMockMvcTest {

    private static final String CLE = "test-api-key";

    /** La cle vient d'une propriete en production ; ici on la pose directement. */
    @TestConfiguration
    static class CleDeTest {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter(CLE);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean RunManager runs;
    @MockitoBean ci.cnam.simulator.security.AuditService audit;
    @MockitoBean ScenarioService scenarios;
    @MockitoBean EventQueryService evenements;

    private static RunInfo run(UUID id, EtatRun etat) {
        return new RunInfo(id, "parcours-normal", 42L, etat, 10L, 100L,
                Instant.parse("2026-01-01T08:00:00Z"), null, null);
    }

    private static Scenario scenario() {
        return new Scenario("parcours-normal", 42L, 1.0d,
                LocalDateTime.parse("2026-01-01T08:00:00"), Map.of(), List.of(), "nom: parcours-normal");
    }

    @Test
    @DisplayName("POST /api/runs lance une simulation et rend 201 avec l'etat du run")
    void lancer() throws Exception {
        UUID id = UUID.randomUUID();
        when(scenarios.charger("parcours-normal")).thenReturn(scenario());
        when(runs.lancer(any())).thenReturn(run(id, EtatRun.EN_COURS));

        mvc.perform(post("/api/runs").header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"parcours-normal\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.etat").value("EN_COURS"))
                .andExpect(jsonPath("$.scenario").value("parcours-normal"));
    }

    @Test
    @DisplayName("pause, resume et stop appellent le gestionnaire de runs")
    void piloter() throws Exception {
        UUID id = UUID.randomUUID();
        when(runs.pause(id)).thenReturn(run(id, EtatRun.EN_PAUSE));
        when(runs.reprendre(id)).thenReturn(run(id, EtatRun.EN_COURS));
        when(runs.arreter(id)).thenReturn(run(id, EtatRun.ARRETE));

        mvc.perform(post("/api/runs/" + id + "/pause").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_PAUSE"));
        mvc.perform(post("/api/runs/" + id + "/resume").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_COURS"));
        mvc.perform(post("/api/runs/" + id + "/stop").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("ARRETE"));

        verify(runs).pause(id);
        verify(runs).reprendre(id);
        verify(runs).arreter(id);
    }

    @Test
    @DisplayName("GET /api/runs et GET /api/runs/{id}")
    void lireLesRuns() throws Exception {
        UUID id = UUID.randomUUID();
        when(runs.lister()).thenReturn(List.of(run(id, EtatRun.ARRETE)));
        when(runs.info(id)).thenReturn(run(id, EtatRun.ARRETE));

        mvc.perform(get("/api/runs").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(get("/api/runs/" + id).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evenementsEmis").value(100));
    }

    @Test
    @DisplayName("sans cle d'API : 401 au format d'erreur de l'API")
    void sansCle() throws Exception {
        mvc.perform(get("/api/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CLE_API_INVALIDE"));
    }

    @Test
    @DisplayName("mauvaise cle d'API : 401")
    void mauvaiseCle() throws Exception {
        mvc.perform(get("/api/runs").header(ApiKeyFilter.ENTETE, "mauvaise-cle"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLE_API_INVALIDE"));
    }

    @Test
    @DisplayName("POST /api/scenarios avec un YAML invalide : 400 SCENARIO_INVALIDE et le detail des erreurs")
    void scenarioInvalide() throws Exception {
        when(scenarios.enregistrer(any()))
                .thenThrow(new ScenarioInvalideException(List.of("domaines : champ obligatoire")));

        mvc.perform(post("/api/scenarios").header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yaml\":\"nom: demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCENARIO_INVALIDE"))
                .andExpect(jsonPath("$.details[0]").value("domaines : champ obligatoire"));
    }

    @Test
    @DisplayName("POST /api/runs sans scenario ni yaml : 400 REQUETE_INVALIDE")
    void lancementSansScenario() throws Exception {
        mvc.perform(post("/api/runs").header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    @Test
    @DisplayName("GET events : filtres transmis au service et page rendue")
    void evenementsFiltres() throws Exception {
        UUID id = UUID.randomUUID();
        EvenementLu evenement = new EvenementLu(UUID.randomUUID(), id, UUID.randomUUID(), "prestations",
                "FACTURE_OUVERTE", LocalDateTime.parse("2026-01-01T08:05:00"),
                Instant.parse("2026-01-01T08:00:01Z"), "ECRIT", Map.of("montant", 1500));
        when(evenements.rechercher(eq(id), eq("prestations"), eq("FACTURE_OUVERTE"), eq("ECRIT"), eq(2), eq(10)))
                .thenReturn(new PageEvenements(List.of(evenement), 2, 10, 45L));

        mvc.perform(get("/api/runs/" + id + "/events").header(ApiKeyFilter.ENTETE, CLE)
                        .param("domaine", "prestations").param("type", "FACTURE_OUVERTE")
                        .param("statut", "ECRIT").param("page", "2").param("taille", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.taille").value(10))
                .andExpect(jsonPath("$.total").value(45))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.contenu[0].type").value("FACTURE_OUVERTE"))
                .andExpect(jsonPath("$.contenu[0].payload.montant").value(1500));
    }

    @Test
    @DisplayName("taille de page au-dela du maximum : 400, la pagination est bornee")
    void paginationBornee() throws Exception {
        mvc.perform(get("/api/runs/" + UUID.randomUUID() + "/events").header(ApiKeyFilter.ENTETE, CLE)
                        .param("taille", String.valueOf(EventQueryService.TAILLE_PAGE_MAX + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    @Test
    @DisplayName("run inconnu : 404 RUN_INCONNU")
    void runInconnu() throws Exception {
        UUID id = UUID.randomUUID();
        when(runs.info(id)).thenThrow(new IllegalArgumentException("Run inconnu : " + id));

        mvc.perform(get("/api/runs/" + id).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_INCONNU"));
    }

    @Test
    @DisplayName("un run est deja actif : 409 ETAT_IMPOSSIBLE")
    void dejaActif() throws Exception {
        when(scenarios.charger("parcours-normal")).thenReturn(scenario());
        when(runs.lancer(any())).thenThrow(new IllegalStateException("Un run est deja actif"));

        mvc.perform(post("/api/runs").header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"parcours-normal\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ETAT_IMPOSSIBLE"));
    }
}

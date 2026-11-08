package ci.cnam.simulator.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.quality.AnalyseAttendue;
import ci.cnam.simulator.quality.AnalyseService;
import ci.cnam.simulator.quality.Echange;
import ci.cnam.simulator.quality.EchangeService;
import ci.cnam.simulator.quality.EtatEchange;
import ci.cnam.simulator.quality.SensEchange;
import ci.cnam.simulator.quality.StatutEchange;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Le canal de retrait vu de l'extremite : ce qu'un script trouve, et ce qu'il ne trouve pas sans
 * cle d'API. Services simules.
 */
@WebMvcTest(controllers = {AnalyseController.class, RunController.class},
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, ApiExceptionHandler.class, AnalyseMockMvcTest.CleDeTest.class})
@EnableAutoConfiguration
class AnalyseMockMvcTest {

    private static final String CLE = "test-api-key";
    private static final Instant T0 = Instant.parse("2026-11-08T10:00:00Z");

    @TestConfiguration
    static class CleDeTest {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter(CLE);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean AnalyseService analyses;
    @MockitoBean EchangeService echanges;
    @MockitoBean RunManager runs;
    @MockitoBean ci.cnam.simulator.core.scenario.ScenarioService scenarios;
    @MockitoBean ci.cnam.simulator.security.AuditService audit;

    private static RunInfo run(UUID id, EtatRun etat) {
        return new RunInfo(id, "parcours-normal", 42L, etat, 10L, 100L, T0, null, null);
    }

    @Test
    @DisplayName("Sans cle d'API : 401 sur les deux nouveaux points d'entree")
    void sansCle() throws Exception {
        mvc.perform(get("/api/analyses/attendues")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/runs/{id}/echanges", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/runs").param("statut", "ARRETE")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Analyses attendues : liste vide et planifiee=false tant que la planification n'existe pas")
    void attenduesSansPlanification() throws Exception {
        when(analyses.attendues()).thenReturn(List.of());
        when(analyses.planificationAbsente()).thenReturn(true);

        mvc.perform(get("/api/analyses/attendues").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planifiee").value(false))
                .andExpect(jsonPath("$.nombre").value(0))
                .andExpect(jsonPath("$.analyses").isArray())
                .andExpect(jsonPath("$.analyses").isEmpty());
    }

    @Test
    @DisplayName("Analyses attendues : la planification branchee, le script y lit la simulation visee")
    void attenduesPlanifiees() throws Exception {
        UUID run = UUID.randomUUID();
        when(analyses.planificationAbsente()).thenReturn(false);
        when(analyses.attendues()).thenReturn(List.of(new AnalyseAttendue(UUID.randomUUID(), run, T0,
                T0.plusSeconds(3600), "ATTENDUE", AnalyseAttendue.cheminExport(run))));

        mvc.perform(get("/api/analyses/attendues").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planifiee").value(true))
                .andExpect(jsonPath("$.nombre").value(1))
                .andExpect(jsonPath("$.analyses[0].runId").value(run.toString()))
                .andExpect(jsonPath("$.analyses[0].statut").value("ATTENDUE"))
                .andExpect(jsonPath("$.analyses[0].export").value("/api/runs/" + run + "/export"));
    }

    @Test
    @DisplayName("GET /api/runs?statut=ARRETE&limit=1 rend la derniere simulation terminee, elle seule")
    void derniereTerminee() throws Exception {
        UUID arrete = UUID.randomUUID();
        when(runs.lister()).thenReturn(List.of(
                run(UUID.randomUUID(), EtatRun.EN_COURS),
                run(arrete, EtatRun.ARRETE),
                run(UUID.randomUUID(), EtatRun.ARRETE)));

        mvc.perform(get("/api/runs").param("statut", "arrete").param("limit", "1")
                        .header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(arrete.toString()))
                .andExpect(jsonPath("$[0].etat").value("ARRETE"));

        mvc.perform(get("/api/runs").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Statut hors liste ou limite a zero : 400, avec les valeurs permises")
    void filtresInvalides() throws Exception {
        mvc.perform(get("/api/runs").param("statut", "TERMINE").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("ARRETE")));
        mvc.perform(get("/api/runs").param("limit", "0").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Journal des echanges : les deux sens, et l'etat qui en decoule")
    void journal() throws Exception {
        UUID run = UUID.randomUUID();
        when(echanges.journal(run)).thenReturn(List.of(
                new Echange(UUID.randomUUID(), run, SensEchange.ENVOI, T0, null,
                        StatutEchange.EXPORT_RETIRE, "Jeu retire au format CSV"),
                new Echange(UUID.randomUUID(), run, SensEchange.RECEPTION, T0.plusSeconds(60), "outil-1.0",
                        StatutEchange.RAPPORT_RECU, "80 detection(s)")));
        when(echanges.etat(run)).thenReturn(Optional.of(
                new EtatEchange(StatutEchange.RAPPORT_RECU, T0.plusSeconds(60), "80 detection(s)")));

        mvc.perform(get("/api/runs/{id}/echanges", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat.statut").value("RAPPORT_RECU"))
                .andExpect(jsonPath("$.etat.echec").value(false))
                .andExpect(jsonPath("$.etat.mesurable").value(true))
                .andExpect(jsonPath("$.echanges.length()").value(2))
                .andExpect(jsonPath("$.echanges[0].sens").value("ENVOI"))
                .andExpect(jsonPath("$.echanges[1].sens").value("RECEPTION"));
    }

    @Test
    @DisplayName("Silence du script : l'etat le dit comme un echec, et aucun chiffre n'apparait")
    void silence() throws Exception {
        UUID run = UUID.randomUUID();
        when(echanges.journal(run)).thenReturn(List.of(
                new Echange(UUID.randomUUID(), run, SensEchange.ENVOI, T0, null,
                        StatutEchange.EXPORT_RETIRE, "Jeu retire au format CSV")));
        when(echanges.etat(run)).thenReturn(Optional.of(
                new EtatEchange(StatutEchange.SILENCE, T0, "aucun rapport dans les 60 minutes")));

        mvc.perform(get("/api/runs/{id}/echanges", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat.statut").value("SILENCE"))
                .andExpect(jsonPath("$.etat.libelle").value("Silence du script"))
                .andExpect(jsonPath("$.etat.echec").value(true))
                .andExpect(jsonPath("$.etat.mesurable").value(false));
    }
}

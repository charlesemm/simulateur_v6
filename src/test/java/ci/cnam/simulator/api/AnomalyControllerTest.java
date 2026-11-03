package ci.cnam.simulator.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.anomaly.AnomalyQueryService;
import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Couche web seule : GET /api/runs/{id}/anomalies, cle d'API, filtres, pagination, erreurs. */
@WebMvcTest(controllers = AnomalyController.class, properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, ApiExceptionHandler.class, AnomalyControllerTest.CleDeTest.class})
@EnableAutoConfiguration
class AnomalyControllerTest {

    private static final String CLE = "test-api-key";

    @TestConfiguration
    static class CleDeTest {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter(CLE);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean AnomalyQueryService anomalies;

    @Test
    void rendLaVeriteTerrainAvecLaValeurDOrigine() throws Exception {
        UUID run = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID facture = UUID.randomUUID();
        when(anomalies.rechercher(eq(run), eq("FAUTE_ORTHOGRAPHE"), eq("nom"), eq(0), eq(50)))
                .thenReturn(new AnomalyQueryService.Page(List.of(new AnomalyRecord(run, event, facture,
                        AnomalyType.FAUTE_ORTHOGRAPHE, "nom", "Kouassi", "Kouasi", null)), 0, 50, 1));

        mvc.perform(get("/api/runs/{id}/anomalies", run).header(ApiKeyFilter.ENTETE, CLE)
                        .param("type", "FAUTE_ORTHOGRAPHE").param("champ", "nom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.contenu[0].eventId").value(event.toString()))
                .andExpect(jsonPath("$.contenu[0].factureId").value(facture.toString()))
                .andExpect(jsonPath("$.contenu[0].type").value("FAUTE_ORTHOGRAPHE"))
                .andExpect(jsonPath("$.contenu[0].champ").value("nom"))
                .andExpect(jsonPath("$.contenu[0].valeurOrigine").value("Kouassi"))
                .andExpect(jsonPath("$.contenu[0].valeurCorrompue").value("Kouasi"));
    }

    @Test
    void laPaginationEstTransmise() throws Exception {
        UUID run = UUID.randomUUID();
        when(anomalies.rechercher(eq(run), eq(null), eq(null), eq(3), eq(20)))
                .thenReturn(new AnomalyQueryService.Page(List.of(), 3, 20, 0));

        mvc.perform(get("/api/runs/{id}/anomalies", run).header(ApiKeyFilter.ENTETE, CLE)
                        .param("page", "3").param("taille", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.taille").value(20));
        verify(anomalies).rechercher(run, null, null, 3, 20);
    }

    @Test
    void sansCleOn401() throws Exception {
        mvc.perform(get("/api/runs/{id}/anomalies", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uneTaillePlusGrandeQueLaBorneEstRefusee() throws Exception {
        mvc.perform(get("/api/runs/{id}/anomalies", UUID.randomUUID()).header(ApiKeyFilter.ENTETE, CLE)
                        .param("taille", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    @Test
    void unTypeInconnuDonne400() throws Exception {
        UUID run = UUID.randomUUID();
        when(anomalies.rechercher(eq(run), eq("BIDON"), eq(null), eq(0), eq(50)))
                .thenThrow(new IllegalArgumentException("type : valeur inconnue 'BIDON'"));

        mvc.perform(get("/api/runs/{id}/anomalies", run).header(ApiKeyFilter.ENTETE, CLE).param("type", "BIDON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }
}

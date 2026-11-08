package ci.cnam.simulator.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.quality.planification.CibleAnalyse;
import ci.cnam.simulator.quality.planification.JoursSemaine;
import ci.cnam.simulator.quality.planification.Planification;
import ci.cnam.simulator.quality.planification.PlanificationService;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.AuditService;
import ci.cnam.simulator.security.SecurityConfig;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Qui peut ecrire le calendrier des analyses. Le controle est fait COTE SERVEUR : un OBSERVATEUR
 * n'obtient rien, meme en postant le formulaire a la main.
 */
@WebMvcTest(controllers = PlanificationController.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, PlanificationWebTest.CleDeTest.class})
@EnableAutoConfiguration
class PlanificationWebTest {

    @TestConfiguration
    static class CleDeTest {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter("cle");
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean PlanificationService planification;
    @MockitoBean AuditService audit;

    private static final UUID ID = UUID.randomUUID();

    private void regleCreee() {
        Instant t0 = Instant.parse("2026-11-08T10:00:00Z");
        when(planification.fuseau()).thenReturn(ZoneId.of("Africa/Abidjan"));
        when(planification.creer(anyString(), any(), anyString(), any())).thenReturn(
                new Planification(ID, "Analyse de nuit", LocalTime.of(3, 0), JoursSemaine.tous(),
                        CibleAnalyse.DERNIERE_SIMULATION_TERMINEE, true, t0, t0));
    }

    @Test
    @DisplayName("Un OBSERVATEUR ne peut pas creer de planification : 403, et rien n'est ecrit")
    @WithMockUser(roles = "OBSERVATEUR")
    void observateurRefuse() throws Exception {
        mvc.perform(post("/qualite/planifications").with(csrf())
                        .param("libelle", "Analyse de nuit").param("heure", "03:00"))
                .andExpect(status().isForbidden());

        verify(planification, never()).creer(anyString(), any(), anyString(), any());
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("Un OBSERVATEUR ne peut ni activer ni supprimer une planification")
    @WithMockUser(roles = "OBSERVATEUR")
    void observateurRefuseLeReste() throws Exception {
        mvc.perform(post("/qualite/planifications/{id}/activer", ID).with(csrf()).param("actif", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/qualite/planifications/{id}/supprimer", ID).with(csrf()))
                .andExpect(status().isForbidden());

        verify(planification, never()).activer(any(), anyBoolean());
        verify(planification, never()).supprimer(any());
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("Un OPERATEUR cree la planification, et la creation passe au journal d'audit")
    @WithMockUser(username = "op", roles = "OPERATEUR")
    void operateurCree() throws Exception {
        regleCreee();

        mvc.perform(post("/qualite/planifications").with(csrf())
                        .param("libelle", "Analyse de nuit").param("heure", "03:00").param("jours", "TOUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qualite"));

        verify(planification).creer("Analyse de nuit", LocalTime.of(3, 0), "TOUS",
                CibleAnalyse.DERNIERE_SIMULATION_TERMINEE);
        verify(audit).enregistrer("op", "PLANIFICATION_CREEE", ID.toString(),
                "Analyse de nuit : 03:00, tous les jours (Africa/Abidjan), cible DERNIERE_SIMULATION_TERMINEE");
    }

    @Test
    @DisplayName("Un ADMIN supprime la planification, et la suppression passe au journal d'audit")
    @WithMockUser(username = "chef", roles = "ADMIN")
    void adminSupprime() throws Exception {
        when(planification.supprimer(ID)).thenReturn(true);

        mvc.perform(post("/qualite/planifications/{id}/supprimer", ID).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qualite"));

        verify(audit).enregistrer(anyString(), org.mockito.ArgumentMatchers.eq("PLANIFICATION_SUPPRIMEE"),
                org.mockito.ArgumentMatchers.eq(ID.toString()), anyString());
    }

    @Test
    @DisplayName("Planification inconnue : refus net, et rien au journal d'audit")
    @WithMockUser(roles = "OPERATEUR")
    void inconnue() throws Exception {
        when(planification.supprimer(ID)).thenReturn(false);

        mvc.perform(post("/qualite/planifications/{id}/supprimer", ID).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("Sans connexion : redirection vers la connexion, aucune ecriture")
    void anonyme() throws Exception {
        mvc.perform(post("/qualite/planifications").with(csrf())
                        .param("libelle", "Analyse").param("heure", "03:00"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(audit);
    }
}

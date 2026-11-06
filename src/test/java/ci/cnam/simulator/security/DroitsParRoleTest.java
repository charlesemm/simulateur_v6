package ci.cnam.simulator.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Les droits sont verifies cote serveur (@PreAuthorize), pas seulement en masquant des boutons. */
@WebMvcTest(controllers = DroitsParRoleTest.PageDeTest.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, DroitsParRoleTest.Support.class})
@EnableAutoConfiguration
class DroitsParRoleTest {

    @RestController
    static class PageDeTest {
        @GetMapping("/test/lecture")
        String lecture() {
            return "ok";
        }

        @PostMapping("/test/ecriture")
        @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
        String ecriture() {
            return "ok";
        }

        @PostMapping("/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "ok";
        }
    }

    @TestConfiguration
    static class Support {
        @Bean
        PageDeTest pageDeTest() {
            return new PageDeTest();
        }

        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter("cle");
        }
    }

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Sans connexion : redirection vers la connexion")
    void anonyme() throws Exception {
        mvc.perform(get("/test/lecture")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "OBSERVATEUR")
    @DisplayName("OBSERVATEUR : lit, mais ne peut ni ecrire ni administrer")
    void observateur() throws Exception {
        mvc.perform(get("/test/lecture")).andExpect(status().isOk());
        mvc.perform(post("/test/ecriture").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/test/admin").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATEUR")
    @DisplayName("OPERATEUR : ecrit, n'administre pas")
    void operateur() throws Exception {
        mvc.perform(post("/test/ecriture").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/test/admin").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN : tout")
    void admin() throws Exception {
        mvc.perform(post("/test/ecriture").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/test/admin").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("CSRF : un POST de l'interface sans jeton est refuse, meme pour un ADMIN")
    void csrfRefuseSansJeton() throws Exception {
        mvc.perform(post("/test/ecriture")).andExpect(status().isForbidden());
    }
}

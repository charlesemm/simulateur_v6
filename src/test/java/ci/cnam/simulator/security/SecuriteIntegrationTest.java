package ci.cnam.simulator.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.persistence.repository.AppUserRepository;
import ci.cnam.simulator.persistence.repository.AuditLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Securite de bout en bout, sur un vrai PostgreSQL : premier ADMIN, connexion, verrouillage, CSRF, API par cle. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecuriteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository utilisateurs;
    @Autowired AuditLogRepository audit;
    @Autowired PasswordEncoder encodeur;
    @Autowired JdbcTemplate jdbc;

    private AppUser compte(String nom, String motDePasse, Role role, boolean aChanger) {
        AppUser u = new AppUser(nom + "-" + UUID.randomUUID().toString().substring(0, 6), encodeur.encode(motDePasse),
                role);
        u.setMotDePasseAChanger(aChanger);
        return utilisateurs.save(u);
    }

    @Test
    @DisplayName("Le premier ADMIN est cree depuis ADMIN_INITIAL_PASSWORD, hache, avec changement obligatoire")
    void premierAdmin() {
        AppUser admin = utilisateurs.findByNomUtilisateur("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isMotDePasseAChanger()).isTrue();
        assertThat(admin.getMotDePasseHash()).startsWith("$2").doesNotContain("test-admin-password");
    }

    @Test
    @DisplayName("La cle d'API est enregistree hachee : la cle en clair n'est nulle part en base")
    void cleHachee() {
        Integer enClair = jdbc.queryForObject("select count(*) from api_key where hash = 'test-api-key'",
                Integer.class);
        Integer hachee = jdbc.queryForObject("select count(*) from api_key where hash = ?", Integer.class,
                ApiKeyService.empreinte("test-api-key"));
        assertThat(enClair).isZero();
        assertThat(hachee).isEqualTo(1);
    }

    @Test
    @DisplayName("Sans connexion, une page de l'interface renvoie vers /login ; /login reste accessible")
    void accesRefuseSansConnexion() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Connexion reussie, puis changement de mot de passe obligatoire tant que le mot de passe est temporaire")
    void changementObligatoire() throws Exception {
        AppUser u = compte("temp", "mot-de-passe-temporaire", Role.OPERATEUR, true);

        var session = mvc.perform(post("/login").with(csrf())
                        .param("username", u.getNomUtilisateur()).param("password", "mot-de-passe-temporaire"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        mvc.perform(get("/dashboard").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/mot-de-passe")));

        mvc.perform(post("/mot-de-passe").with(csrf()).session((org.springframework.mock.web.MockHttpSession) session)
                        .param("ancien", "mot-de-passe-temporaire")
                        .param("nouveau", "nouveau-mot-de-passe-solide")
                        .param("confirmation", "nouveau-mot-de-passe-solide"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/dashboard").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("5 echecs verrouillent le compte : meme le bon mot de passe est refuse, avec le meme message")
    void verrouillageApresCinqEchecs() throws Exception {
        AppUser u = compte("verrou", "le-bon-mot-de-passe", Role.OBSERVATEUR, false);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login").with(csrf())
                            .param("username", u.getNomUtilisateur()).param("password", "mauvais"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mvc.perform(post("/login").with(csrf())
                        .param("username", u.getNomUtilisateur()).param("password", "le-bon-mot-de-passe"))
                .andExpect(redirectedUrl("/login?error"));
        assertThat(utilisateurs.findByNomUtilisateur(u.getNomUtilisateur()).orElseThrow().getVerrouilleJusqua())
                .isNotNull();
        assertThat(audit.findAll()).anyMatch(a -> a.getAction().equals("COMPTE_VERROUILLE")
                && a.getCible().equals(u.getNomUtilisateur()));
    }

    @Test
    @DisplayName("Un compte inconnu recoit exactement la meme reponse qu'un mauvais mot de passe")
    void messageGenerique() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "personne").param("password", "x"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("CSRF actif sur le formulaire : un POST sans jeton est refuse")
    void csrfActif() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'API reste protegee par la cle : 401 sans cle, 200 avec la cle, sans CSRF ni session")
    void apiParCle() throws Exception {
        mvc.perform(get("/api/runs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/runs").header(ApiKeyFilter.ENTETE, "mauvaise")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/runs").header(ApiKeyFilter.ENTETE, "test-api-key")).andExpect(status().isOk());
    }
}

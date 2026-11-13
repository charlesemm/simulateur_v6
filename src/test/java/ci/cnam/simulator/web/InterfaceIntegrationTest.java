package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.SupportRun;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** L'interface de bout en bout sur un vrai PostgreSQL : vrai moteur, vraies tables, vrai journal d'audit. */
@SpringBootTest(properties = {"simulateur.ecriture.delai-max-ms=50"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InterfaceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RunManager runs;

    private UUID runAvecEvenements(UUID evenementEchoue) {
        UUID run = SupportRun.nouveauRun(jdbc);
        jdbc.update("""
                insert into "TB_ECHO_EVENEMENT" (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel, payload, statut)
                values (?, ?, 'prestations', 'FACTURE_OUVERTE', now(), now(), '{"nom":"Kone"}'::jsonb, 'ECRIT')""",
                UUID.randomUUID(), run);
        jdbc.update("""
                insert into "TB_ECHO_EVENEMENT" (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel, payload, statut)
                values (?, ?, 'prestations', '=FORMULE', now(), now(), '{"nom":"=1+1"}'::jsonb, 'ECHEC')""",
                evenementEchoue, run);
        return run;
    }

    private int lignesAudit(String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ?", Integer.class, action);
    }

    @Test
    @DisplayName("Explorateur : filtres reels, export CSV avec formule neutralisee, rejeu d'un evenement echoue")
    void explorateurExportEtRejeu() throws Exception {
        UUID echoue = UUID.randomUUID();
        UUID run = runAvecEvenements(echoue);
        var operateur = user("operateur").roles("OPERATEUR");

        mvc.perform(get("/evenements").param("run", run.toString()).param("statut", "ECHEC").with(operateur))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1</strong> événement")))
                .andExpect(content().string(containsString("Rejouer")));

        MvcResult debut = mvc.perform(get("/evenements/export.csv").param("run", run.toString()).with(operateur))
                .andExpect(request().asyncStarted()).andReturn();
        String csv = mvc.perform(asyncDispatch(debut)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).contains("FACTURE_OUVERTE").contains(",'=FORMULE,").contains("\"{\"\"nom\"\":\"\"Kone\"\"}\"");

        mvc.perform(post("/evenements/{id}/rejouer", echoue).with(csrf()).with(operateur))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select statut from \"TB_ECHO_EVENEMENT\" where event_id = ?", String.class, echoue))
                .isEqualTo("ECRIT");
        assertThat(lignesAudit("EVENEMENT_REJOUE")).isGreaterThanOrEqualTo(1);

        mvc.perform(post("/evenements/{id}/rejouer", echoue).with(csrf()).with(operateur))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("deja ecrit")));
        mvc.perform(post("/evenements/{id}/rejouer", UUID.randomUUID()).with(csrf()).with(operateur))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("inconnu")));
    }

    @Test
    @DisplayName("L'OBSERVATEUR ne peut pas rejouer : le statut en base ne change pas")
    void observateurNeRejouePas() throws Exception {
        UUID echoue = UUID.randomUUID();
        runAvecEvenements(echoue);

        mvc.perform(post("/evenements/{id}/rejouer", echoue).with(csrf()).with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select statut from \"TB_ECHO_EVENEMENT\" where event_id = ?", String.class, echoue))
                .isEqualTo("ECHEC");
    }

    @Test
    @DisplayName("Scenarios : generer, valider, enregistrer en base, retrouver dans la liste et exporter")
    void editeurDeScenarios() throws Exception {
        var operateur = user("operateur").roles("OPERATEUR");
        String nom = "Test interface " + UUID.randomUUID().toString().substring(0, 6);

        String zone = mvc.perform(post("/scenarios/generer").with(csrf()).with(operateur)
                        .param("nom", nom).param("seed", "5").param("accelerationTemps", "60").param("debit", "2")
                        .param("examens", "60").param("hospitalisation", "10").param("direct", "30")
                        .param("anomaliesGlobal", "2")
                        .param("phaseDe", "0m", "", "").param("phaseA", "1m", "", "")
                        .param("phaseCharge", "normale", "", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Scénario valide")))
                .andReturn().getResponse().getContentAsString();
        assertThat(zone).contains("seed: 5").contains("global: 2%");

        String yaml = "nom: \"" + nom + "\"\nseed: 5\ndomaines:\n  prestations:\n    debit: 2/s\n";
        mvc.perform(post("/scenarios/enregistrer").with(csrf()).with(operateur).param("yaml", yaml))
                .andExpect(status().isOk()).andExpect(content().string(containsString("enregistré")));
        assertThat(jdbc.queryForObject("select count(*) from scenario where nom = ?", Integer.class, nom)).isEqualTo(1);

        mvc.perform(get("/scenarios").with(operateur)).andExpect(content().string(containsString(nom)));
        mvc.perform(get("/scenarios/export").param("nom", nom).with(operateur))
                .andExpect(status().isOk()).andExpect(content().string(yaml));

        mvc.perform(post("/scenarios/enregistrer").with(csrf()).with(operateur).param("yaml", "nom: x\nseed: abc\n"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("seed")));
    }

    @Test
    @DisplayName("Parcours complet : lancer, chaos, tout retablir, pause, reprise, arret, historique, audit")
    void parcoursDePilotage() throws Exception {
        var operateur = user("operateur").roles("OPERATEUR");

        mvc.perform(post("/lancement").with(csrf()).with(operateur)
                        .param("scenario", "parcours-normal").param("graine", "123").param("vitesse", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lancé (graine 123)")));
        var actif = runs.actif().orElseThrow();
        assertThat(actif.seed()).isEqualTo(123L);
        assertThat(actif.etat()).isEqualTo(EtatRun.EN_COURS);

        // Un second lancement est refuse proprement.
        mvc.perform(post("/lancement").with(csrf()).with(operateur).param("scenario", "parcours-normal"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("deja actif")));

        mvc.perform(post("/chaos/declencher").with(csrf()).with(operateur)
                        .param("type", "SURCHARGE").param("multiplicateur", "5").param("dureeSecondes", "20"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("SURCHARGE")));
        mvc.perform(post("/chaos/declencher").with(csrf()).with(operateur)
                        .param("type", "SURCHARGE").param("multiplicateur", "7").param("dureeSecondes", "20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("multiplicateur")));
        mvc.perform(post("/chaos/retablir").with(csrf()).with(operateur))
                .andExpect(status().isOk()).andExpect(content().string(containsString("tout est rétabli")));

        mvc.perform(post("/lancement/{id}/pause", actif.id()).with(csrf()).with(operateur)).andExpect(status().isOk());
        assertThat(runs.info(actif.id()).etat()).isEqualTo(EtatRun.EN_PAUSE);
        mvc.perform(post("/lancement/{id}/reprendre", actif.id()).with(csrf()).with(operateur))
                .andExpect(status().isOk());
        mvc.perform(post("/lancement/{id}/arreter", actif.id()).with(csrf()).with(operateur))
                .andExpect(status().isOk());
        assertThat(runs.info(actif.id()).etat()).isEqualTo(EtatRun.ARRETE);

        // L'ancienne adresse reste servie ; la liste mene a la fiche, rendue sur de vraies tables.
        mvc.perform(get("/historique").with(operateur)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/simulations/" + actif.id())));
        mvc.perform(get("/simulations/{id}", actif.id()).with(operateur)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Surcharge")))
                .andExpect(content().string(containsString("Rétablie à la main")));

        // Rejouer avec la meme graine : nouveau run, meme graine.
        mvc.perform(post("/lancement/{id}/rejouer", actif.id()).with(csrf()).with(operateur))
                .andExpect(status().isOk()).andExpect(content().string(containsString("graine 123")));
        var rejoue = runs.actif().orElseThrow();
        assertThat(rejoue.id()).isNotEqualTo(actif.id());
        assertThat(rejoue.seed()).isEqualTo(123L);
        mvc.perform(post("/lancement/{id}/arreter", rejoue.id()).with(csrf()).with(operateur))
                .andExpect(status().isOk());

        List<String> actions = jdbc.queryForList("select action from audit_log where acteur = 'operateur'",
                String.class);
        assertThat(actions).contains("RUN_LANCE", "CHAOS_DECLENCHE", "CHAOS_RETABLI", "RUN_PAUSE", "RUN_REPRIS",
                "RUN_ARRETE", "RUN_REJOUE");
    }

    @Test
    @DisplayName("Tableau de bord et flux : la page se rend, le flux SSE s'ouvre")
    void tableauDeBordEtFlux() throws Exception {
        var observateur = user("obs").roles("OBSERVATEUR");

        mvc.perform(get("/dashboard").with(observateur)).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-flux=\"/flux\"")));
        mvc.perform(get("/flux").with(observateur)).andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("Administration : un ADMIN cree un compte avec mot de passe temporaire, l'operation est auditee")
    void administration() throws Exception {
        var admin = user("admin").roles("ADMIN");
        String nom = "agent" + UUID.randomUUID().toString().substring(0, 6);

        mvc.perform(post("/admin/utilisateurs").with(csrf()).with(admin)
                        .param("nom", nom).param("motDePasse", "mot-de-passe-temporaire").param("role", "OBSERVATEUR"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select mot_de_passe_a_changer from app_user where nom_utilisateur = ?",
                Boolean.class, nom)).isTrue();
        assertThat(jdbc.queryForObject("select mot_de_passe_hash from app_user where nom_utilisateur = ?",
                String.class, nom)).startsWith("$2").doesNotContain("mot-de-passe-temporaire");
        mvc.perform(get("/admin/utilisateurs").with(admin)).andExpect(content().string(containsString(nom)));
        // Le journal a son propre ecran, et dit l'action en clair.
        mvc.perform(get("/admin/journal").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString(nom)))
                .andExpect(content().string(containsString("Compte créé")));
    }
}

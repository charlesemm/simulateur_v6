package ci.cnam.simulator.quality.planification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.security.ApiKeyFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Le calendrier sur un vrai PostgreSQL : l'unicite qui rend la tache idempotente, et le CYCLE
 * COMPLET — echeance ouverte, lue par l'API, export retire, rapport pousse, RAPPORT_RECU.
 *
 * <p>L'horloge de l'application est remplacee par une horloge pilotee : rien n'attend.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(PlanificationIntegrationTest.HorlogePilotee.class)
class PlanificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final String CLE = "test-api-key";

    /** Horloge mutable : ClockConfig cede la place (@ConditionalOnMissingBean). */
    static class Pilote extends Clock {
        private Instant maintenant = Instant.parse("2026-11-09T02:00:00Z");

        void a(String instant) {
            maintenant = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    @TestConfiguration
    static class HorlogePilotee {
        /** Un SEUL bean de type Clock : il porte le nom du bean d'origine, qu'il remplace. */
        @Bean
        Pilote clock() {
            return new Pilote();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanificationService planification;
    @Autowired PlanificationStore store;
    @Autowired Pilote horloge;

    @BeforeEach
    void table() {
        jdbc.update("delete from \"TB_ECHO_ANALYSE_ATTENDUE\"");
        jdbc.update("delete from \"TB_ECHO_PLANIFICATION_ANALYSE\"");
        // Les runs d'un test precedent feraient une « derniere simulation terminee » involontaire :
        // le test « sans cible » n'en veut aucune, et les autres veulent la leur.
        jdbc.update("delete from \"TB_ECHO_SIMULATION_RUN\"");
        jdbc.update("delete from scenario");
        horloge.a("2026-11-09T02:00:00Z");
    }

    /** Un run TERMINE : l'export le refuserait sinon. */
    private UUID runTermine(int evenements) {
        UUID scenario = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        jdbc.update("insert into scenario (id, nom, contenu_yaml) values (?, ?, 'nom: test')",
                scenario, "planif-" + scenario);
        jdbc.update("""
                insert into "TB_ECHO_SIMULATION_RUN" (id, scenario_id, seed, statut, debut_simule, parametres, date_fin)
                values (?, ?, 42, 'ARRETE', timestamp '2026-01-02 00:00:00', '{}'::jsonb, ?)""",
                run, scenario, java.time.OffsetDateTime.parse("2026-11-09T01:00:00Z"));
        for (int i = 0; i < evenements; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into "TB_ECHO_EVENEMENT" (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel, payload)
                    values (?, ?, 'prestations', 'FACTURE_OUVERTE', timestamp '2026-01-02 08:00:00',
                            timestamptz '2026-01-02 08:00:00+00', '{}'::jsonb)""", id, run);
            jdbc.update("insert into \"TB_ECHO_ANOMALY_INJECTED\" (run_id, event_id, type, champ) "
                    + "values (?, ?, 'CHAMP_VIDE', 'nom')", run, id);
            dernierEvenement = id;
        }
        return run;
    }

    private UUID dernierEvenement;

    private Planification regleA3h() {
        return planification.creer("Analyse de nuit", LocalTime.of(3, 0), JoursSemaine.TOUS, null);
    }

    private List<Map<String, Object>> echeances() {
        return jdbc.queryForList("select planification_id, run_id, date_echeance, date_retrait, "
                + "date_rapport, statut, detail from \"TB_ECHO_ANALYSE_ATTENDUE\" order by date_echeance");
    }

    @Test
    @DisplayName("Le cycle complet : echeance ouverte, lue par l'API, export retire, rapport pousse, RAPPORT_RECU")
    void cycleComplet() throws Exception {
        UUID run = runTermine(3);
        regleA3h();

        horloge.a("2026-11-09T03:00:00Z");
        assertThat(planification.ouvrirEcheancesDues()).isEqualTo(1);
        assertThat(echeances()).hasSize(1);
        assertThat(echeances().get(0))
                .containsEntry("statut", "ATTENDUE").containsEntry("run_id", run);

        // 1. Le script lit ce qu'Echo attend. La planification est desormais branchee.
        mvc.perform(get("/api/analyses/attendues").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planifiee").value(true))
                .andExpect(jsonPath("$.nombre").value(1))
                .andExpect(jsonPath("$.analyses[0].runId").value(run.toString()))
                .andExpect(jsonPath("$.analyses[0].statut").value("ATTENDUE"))
                .andExpect(jsonPath("$.analyses[0].export").value("/api/runs/" + run + "/export"));

        // 2. Il retire l'export au chemin que l'API lui a donne.
        horloge.a("2026-11-09T03:05:00Z");
        MvcResult debut = mvc.perform(get("/api/runs/{id}/export.csv", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(mvc.perform(asyncDispatch(debut)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).isNotEmpty();

        assertThat(echeances().get(0)).containsEntry("statut", "RETIREE");
        // Retiree : elle n'est plus a prendre.
        mvc.perform(get("/api/analyses/attendues").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(jsonPath("$.nombre").value(0));

        // 3. Il pousse son rapport.
        horloge.a("2026-11-09T03:10:00Z");
        mvc.perform(post("/api/runs/{id}/quality/report", run).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"temoin-python-1.0\",\"detections\":[{\"eventId\":\""
                                + dernierEvenement + "\",\"type\":\"CHAMP_VIDE\",\"champ\":\"nom\"}]}"))
                .andExpect(status().isCreated());

        Map<String, Object> echeance = echeances().get(0);
        assertThat(echeance).containsEntry("statut", "RAPPORT_RECU");
        assertThat(echeance.get("date_retrait")).isNotNull();
        assertThat(echeance.get("date_rapport")).isNotNull();

        // Et le delai depasse ne la fait plus tomber en silence : elle est honoree.
        horloge.a("2026-11-10T00:00:00Z");
        assertThat(planification.marquerSilences()).isZero();
    }

    @Test
    @DisplayName("Deux passages dans la meme minute : la base n'ouvre qu'une echeance")
    void idempotenceEnBase() {
        runTermine(1);
        regleA3h();
        horloge.a("2026-11-09T03:00:00Z");

        assertThat(planification.ouvrirEcheancesDues()).isEqualTo(1);
        assertThat(planification.ouvrirEcheancesDues()).isZero();
        horloge.a("2026-11-09T03:00:45Z");
        assertThat(planification.ouvrirEcheancesDues()).isZero();

        assertThat(jdbc.queryForObject("select count(*) from \"TB_ECHO_ANALYSE_ATTENDUE\"", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Aucune simulation ARRETE : l'echeance s'ouvre quand meme, SANS_CIBLE, sans run_id")
    void sansCible() throws Exception {
        // Aucun run ARRETE n'est cree ici, et un run EN_COURS n'en est pas un.
        regleA3h();
        horloge.a("2026-11-09T03:00:00Z");

        assertThat(planification.ouvrirEcheancesDues()).isEqualTo(1);

        Map<String, Object> echeance = echeances().get(0);
        assertThat(echeance).containsEntry("statut", "SANS_CIBLE").containsEntry("run_id", null);
        assertThat(String.valueOf(echeance.get("detail"))).contains("Aucune simulation terminee");

        // Rien a retirer : le script ne la voit pas.
        mvc.perform(get("/api/analyses/attendues").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planifiee").value(true))
                .andExpect(jsonPath("$.nombre").value(0));
    }

    @Test
    @DisplayName("Echeance retiree sans rapport, delai depasse : SILENCE en base, et aucun taux")
    void silence() throws Exception {
        UUID run = runTermine(2);
        regleA3h();
        horloge.a("2026-11-09T03:00:00Z");
        planification.ouvrirEcheancesDues();

        horloge.a("2026-11-09T03:05:00Z");
        MvcResult debut = mvc.perform(get("/api/runs/{id}/export.csv", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(debut)).andExpect(status().isOk());
        assertThat(echeances().get(0)).containsEntry("statut", "RETIREE");

        // Avant le delai : rien.
        horloge.a("2026-11-09T04:00:00Z");
        assertThat(planification.marquerSilences()).isZero();

        // Apres : SILENCE, et aucun chiffre de detection nulle part.
        horloge.a("2026-11-09T04:06:00Z");
        assertThat(planification.marquerSilences()).isEqualTo(1);
        assertThat(echeances().get(0)).containsEntry("statut", "SILENCE");
        assertThat(planification.marquerSilences()).isZero();

        mvc.perform(get("/api/runs/{id}/quality/comparison", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.echange.statut").value("SILENCE"));
    }

    @Test
    @DisplayName("Rapport refuse : l'echeance passe en RAPPORT_REFUSE, un echec et non un taux")
    void rapportRefuse() throws Exception {
        UUID run = runTermine(1);
        regleA3h();
        horloge.a("2026-11-09T03:00:00Z");
        planification.ouvrirEcheancesDues();

        horloge.a("2026-11-09T03:10:00Z");
        mvc.perform(post("/api/runs/{id}/quality/report", run).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"temoin-python-1.0\",\"detections\":[{\"eventId\":\""
                                + dernierEvenement + "\",\"type\":\"TYPE_QUI_N_EXISTE_PAS\"}]}"))
                .andExpect(status().isBadRequest());

        assertThat(echeances().get(0)).containsEntry("statut", "RAPPORT_REFUSE");
    }

    @Test
    @DisplayName("Planification supprimee : plus aucune echeance ne s'ouvre, l'historique survit")
    void supprimee() {
        runTermine(1);
        Planification regle = regleA3h();
        horloge.a("2026-11-09T03:00:00Z");
        planification.ouvrirEcheancesDues();

        assertThat(planification.supprimer(regle.id())).isTrue();

        // L'echeance deja ouverte reste, rattachee a plus rien : ce qui a eu lieu reste tracable.
        assertThat(echeances().get(0)).containsEntry("planification_id", null);
        horloge.a("2026-11-10T03:00:00Z");
        assertThat(planification.ouvrirEcheancesDues()).isZero();
        assertThat(store.planifications()).isEmpty();
    }
}

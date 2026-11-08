package ci.cnam.simulator.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.security.ApiKeyFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * Le cycle complet du canal de retrait sur un vrai PostgreSQL : le script retire le jeu, pousse son
 * rapport, et les deux echanges sont inscrits, horodates, dans le bon ordre.
 *
 * <p>Et surtout : les trois echecs d'echange ne produisent AUCUN chiffre de detection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class EchangeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final String CLE = "test-api-key";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** Un run TERMINE (l'export le refuserait sinon), avec des evenements et des anomalies semees. */
    private record Jeu(UUID run, List<UUID> evenements) {
    }

    private Jeu jeuTermine(int nombre) {
        UUID scenario = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        jdbc.update("insert into scenario (id, nom, contenu_yaml) values (?, ?, 'nom: test')", scenario,
                "echange-" + scenario);
        jdbc.update("""
                insert into simulation_run (id, scenario_id, seed, statut, debut_simule, parametres)
                values (?, ?, 42, 'ARRETE', timestamp '2026-01-02 00:00:00', '{}'::jsonb)""", run, scenario);
        List<UUID> evenements = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into evenement (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel, payload)
                    values (?, ?, 'prestations', 'FACTURE_OUVERTE', timestamp '2026-01-02 08:00:00',
                            timestamptz '2026-01-02 08:00:00+00', '{}'::jsonb)""", id, run);
            jdbc.update("insert into anomaly_injected (run_id, event_id, type, champ) values (?, ?, 'CHAMP_VIDE', ?)",
                    run, id, "nom");
            evenements.add(id);
        }
        return new Jeu(run, evenements);
    }

    /** Le retrait part en flux : MockMvc demande donc le second temps de la requete asynchrone. */
    private byte[] retirer(UUID run, String extension) throws Exception {
        MvcResult debut = mvc.perform(get("/api/runs/{id}/export.{ext}", run, extension)
                        .header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(debut)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static String detection(UUID evenement, String type, String champ) {
        return "{\"eventId\":\"" + evenement + "\",\"type\":\"" + type + "\""
                + (champ == null ? "" : ",\"champ\":\"" + champ + "\"") + "}";
    }

    private List<Map<String, Object>> echanges(UUID run) {
        return jdbc.queryForList(
                "select sens, statut, version_outil, date_echange from echange where run_id = ? "
                        + "order by date_echange, id", run);
    }

    @Test
    @DisplayName("Retrait puis rapport : deux lignes dans echange, horodatees, dans le bon ordre")
    void cycleComplet() throws Exception {
        Jeu jeu = jeuTermine(5);

        assertThat(retirer(jeu.run(), "csv")).isNotEmpty();

        mvc.perform(post("/api/runs/{id}/quality/report", jeu.run()).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"temoin-python-1.0\",\"detections\":["
                                + detection(jeu.evenements().get(0), "CHAMP_VIDE", "nom") + "]}"))
                .andExpect(status().isCreated());

        List<Map<String, Object>> lignes = echanges(jeu.run());
        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0)).containsEntry("sens", "ENVOI").containsEntry("statut", "EXPORT_RETIRE");
        assertThat(lignes.get(0).get("version_outil")).isNull();
        assertThat(lignes.get(1)).containsEntry("sens", "RECEPTION").containsEntry("statut", "RAPPORT_RECU")
                .containsEntry("version_outil", "temoin-python-1.0");
        assertThat(instant(lignes.get(0))).isBeforeOrEqualTo(instant(lignes.get(1)));

        // Le rapport est arrive : la mesure est possible, et l'etat le dit.
        mvc.perform(get("/api/runs/{id}/echanges", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat.statut").value("RAPPORT_RECU"))
                .andExpect(jsonPath("$.etat.mesurable").value(true))
                .andExpect(jsonPath("$.echanges.length()").value(2));
        mvc.perform(get("/api/runs/{id}/quality/comparison", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.injectees").value(5))
                .andExpect(jsonPath("$.total.detectees").value(1));

        // L'ecran montre les deux echanges horodates.
        String ecran = mvc.perform(get("/export").param("run", jeu.run().toString())
                        .with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(ecran)
                .contains("changes avec l'outil d'analyse")   // le titre de la carte
                .contains("Jeu retir")                        // le sens de l'envoi
                .contains("Rapport pouss")                    // le sens de la reception
                .contains("Rapport recu")                     // le statut
                .contains("temoin-python-1.0");
    }

    @Test
    @DisplayName("ECHEC 1 - Retrait sans rapport, delai depasse : SILENCE, et AUCUN taux (ni 0 %)")
    void silence() throws Exception {
        Jeu jeu = jeuTermine(5);
        retirer(jeu.run(), "csv");

        // Le delai est de 60 minutes : on recule l'envoi de deux heures plutot que d'attendre.
        jdbc.update("update echange set date_echange = date_echange - interval '2 hours' where run_id = ?",
                jeu.run());

        mvc.perform(get("/api/runs/{id}/echanges", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat.statut").value("SILENCE"))
                .andExpect(jsonPath("$.etat.echec").value(true))
                .andExpect(jsonPath("$.etat.mesurable").value(false));

        // 5 anomalies semees, 0 detection : le piege serait de rendre un taux de 0 %.
        mvc.perform(get("/api/runs/{id}/quality/comparison", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echange.statut").value("SILENCE"))
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.types").isEmpty())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("tauxDetection"))));

        String ecran = mvc.perform(get("/qualite").param("run", jeu.run().toString())
                        .with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(ecran).contains("Silence du script").doesNotContain("0,0 %").doesNotContain("0.0 %");
    }

    @Test
    @DisplayName("ECHEC 2 - Envoi refuse en bloc : RAPPORT_MALFORME inscrit malgre l'annulation, aucun chiffre")
    void rapportMalforme() throws Exception {
        Jeu jeu = jeuTermine(5);
        retirer(jeu.run(), "csv");

        mvc.perform(post("/api/runs/{id}/quality/report", jeu.run()).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"outil-1.0\",\"detections\":["
                                + detection(UUID.randomUUID(), "CHAMP_VIDE", "nom") + "]}"))
                .andExpect(status().isUnprocessableEntity());

        // L'envoi a ete annule (aucun rapport enregistre), la TRACE de la tentative a survecu.
        assertThat(jdbc.queryForObject("select count(*) from quality_report where run_id = ?", Integer.class,
                jeu.run())).isZero();
        assertThat(echanges(jeu.run())).hasSize(2);
        assertThat(dernier(jeu.run())).containsEntry("statut", "RAPPORT_MALFORME").containsEntry("sens", "RECEPTION");

        mvc.perform(get("/api/runs/{id}/quality/comparison", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echange.statut").value("RAPPORT_MALFORME"))
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    @DisplayName("ECHEC 3 - Toutes les detections sans champ exploitable : RAPPORT_SANS_DETAIL, aucun chiffre")
    void rapportSansDetail() throws Exception {
        Jeu jeu = jeuTermine(5);
        retirer(jeu.run(), "csv");

        // Accepte syntaxiquement (le champ est facultatif pour un doublon), mais aucune detection ne
        // porte a la fois un type et un champ : aucune mesure fiable n'est possible (cahier 4.3).
        mvc.perform(post("/api/runs/{id}/quality/report", jeu.run()).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"outil-global-1.0\",\"detections\":["
                                + detection(jeu.evenements().get(0), "DOUBLON_EXACT", null) + ","
                                + detection(jeu.evenements().get(1), "DOUBLON_EXACT", null) + "]}"))
                .andExpect(status().isCreated());   // le contrat du POST ne change pas

        assertThat(echanges(jeu.run())).hasSize(2);
        assertThat(dernier(jeu.run())).containsEntry("statut", "RAPPORT_SANS_DETAIL");

        mvc.perform(get("/api/runs/{id}/quality/comparison", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echange.statut").value("RAPPORT_SANS_DETAIL"))
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.types").isEmpty());

        String ecran = mvc.perform(get("/qualite").param("run", jeu.run().toString())
                        .with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(ecran).contains("Rapport sans detail exploitable")
                .doesNotContain("tauxDetection");
    }

    @Test
    @DisplayName("Rapport pousse sans retrait prealable : accepte et trace, le script peut travailler seul")
    void rapportSansRetrait() throws Exception {
        Jeu jeu = jeuTermine(5);

        mvc.perform(post("/api/runs/{id}/quality/report", jeu.run()).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"hors-planification\",\"detections\":["
                                + detection(jeu.evenements().get(0), "CHAMP_VIDE", "nom") + "]}"))
                .andExpect(status().isCreated());

        assertThat(echanges(jeu.run())).hasSize(1);
        assertThat(dernier(jeu.run())).containsEntry("sens", "RECEPTION").containsEntry("statut", "RAPPORT_RECU");
        mvc.perform(get("/api/runs/{id}/quality/comparison", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.detectees").value(1));
    }

    @Test
    @DisplayName("Un script trouve la derniere simulation terminee, puis son export, sous la seule cle d'API")
    void parcoursDuScript() throws Exception {
        Jeu jeu = jeuTermine(3);

        String reponse = mvc.perform(get("/api/runs").param("statut", "ARRETE").param("limit", "1")
                        .header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].etat").value("ARRETE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(reponse).contains("ARRETE");

        mvc.perform(get("/api/analyses/attendues").header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                // Depuis le prompt 15, la planification est branchee : planifiee vaut true. La liste
                // reste vide tant qu'aucune regle de calendrier n'a ouvert d'echeance, et le script
                // travaille alors hors planification, ce qui est son droit.
                .andExpect(jsonPath("$.planifiee").value(true))
                .andExpect(jsonPath("$.analyses").isEmpty());

        assertThat(retirer(jeu.run(), "csv")).isNotEmpty();
    }

    /** La derniere ligne du journal des echanges. */
    private Map<String, Object> dernier(UUID run) {
        List<Map<String, Object>> lignes = echanges(run);
        return lignes.get(lignes.size() - 1);
    }

    private static Instant instant(Map<String, Object> ligne) {
        return ((java.sql.Timestamp) ligne.get("date_echange")).toInstant();
    }
}

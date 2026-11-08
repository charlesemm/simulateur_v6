package ci.cnam.simulator.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.SupportRun;
import ci.cnam.simulator.security.ApiKeyFilter;
import java.util.ArrayList;
import java.util.List;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Rapport qualite de bout en bout sur un vrai PostgreSQL, par l'API (cle d'API) et par l'ecran. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class QualiteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final String CLE = "test-api-key";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** Un run avec 100 anomalies injectees : 40 CHAMP_VIDE, 30 FORMAT_DATE_INVALIDE, 30 FAUTE_ORTHOGRAPHE. */
    private record Jeu(UUID run, List<UUID> evenements) {
    }

    private UUID evenement(UUID run) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into evenement (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel, payload)
                values (?, ?, 'prestations', 'FACTURE_OUVERTE', now(), now(), '{}'::jsonb)""", id, run);
        return id;
    }

    private void injectee(UUID run, UUID evenement, String type, String champ) {
        jdbc.update("insert into anomaly_injected (run_id, event_id, type, champ) values (?, ?, ?, ?)",
                run, evenement, type, champ);
    }

    private Jeu jeuDe100() {
        UUID run = SupportRun.nouveauRun(jdbc);
        List<UUID> evenements = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UUID e = evenement(run);
            evenements.add(e);
            injectee(run, e, i < 40 ? "CHAMP_VIDE" : i < 70 ? "FORMAT_DATE_INVALIDE" : "FAUTE_ORTHOGRAPHE",
                    i < 40 ? "nom" : i < 70 ? "date_naissance" : "prenoms");
        }
        return new Jeu(run, evenements);
    }

    private static String detection(UUID evenement, String type, String champ) {
        return "{\"eventId\":\"" + evenement + "\",\"type\":\"" + type + "\",\"champ\":\"" + champ
                + "\",\"regle\":\"R1\",\"score\":0.9}";
    }

    /** 80 bonnes detections (35 + 25 + 20) et 10 faux positifs (4 + 3 + 3) sur des evenements sans anomalie. */
    private List<String> detectionsDuJeu(Jeu jeu) {
        List<String> d = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UUID e = jeu.evenements().get(i);
            if (i < 35) {
                d.add(detection(e, "CHAMP_VIDE", "nom"));
            } else if (i >= 40 && i < 65) {
                d.add(detection(e, "FORMAT_DATE_INVALIDE", "date_naissance"));
            } else if (i >= 70 && i < 90) {
                d.add(detection(e, "FAUTE_ORTHOGRAPHE", "prenoms"));
            }
        }
        String[][] faux = {{"CHAMP_VIDE", "nom", "4"}, {"FORMAT_DATE_INVALIDE", "date_naissance", "3"},
                {"FAUTE_ORTHOGRAPHE", "prenoms", "3"}};
        for (String[] f : faux) {
            for (int i = 0; i < Integer.parseInt(f[2]); i++) {
                d.add(detection(evenement(jeu.run()), f[0], f[1]));   // evenement du run, jamais corrompu
            }
        }
        return d;
    }

    private void envoyer(UUID run, String version, List<String> detections) throws Exception {
        mvc.perform(post("/api/runs/{id}/quality/report", run).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"" + version + "\",\"detections\":[" + String.join(",", detections)
                                + "]}"))
                .andExpect(status().isCreated());
    }

    private String comparaison(UUID run) throws Exception {
        return mvc.perform(get("/api/runs/{id}/quality/comparison", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("100 injectees, 80 detectees, 20 ratees, 10 faux positifs : chiffres exacts, par type")
    void jeuConnu() throws Exception {
        Jeu jeu = jeuDe100();
        envoyer(jeu.run(), "qualite-1.0", detectionsDuJeu(jeu));

        mvc.perform(get("/api/runs/{id}/quality/comparison", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(false))
                .andExpect(jsonPath("$.total.injectees").value(100))
                .andExpect(jsonPath("$.total.detectees").value(80))
                .andExpect(jsonPath("$.total.ratees").value(20))
                .andExpect(jsonPath("$.total.fauxPositifs").value(10))
                .andExpect(jsonPath("$.total.tauxDetection").value(0.8))
                .andExpect(jsonPath("$.total.precision").value(0.8889))
                .andExpect(jsonPath("$.types[?(@.libelle=='CHAMP_VIDE')].injectees").value(40))
                .andExpect(jsonPath("$.types[?(@.libelle=='CHAMP_VIDE')].detectees").value(35))
                .andExpect(jsonPath("$.types[?(@.libelle=='CHAMP_VIDE')].ratees").value(5))
                .andExpect(jsonPath("$.types[?(@.libelle=='CHAMP_VIDE')].fauxPositifs").value(4))
                .andExpect(jsonPath("$.types[?(@.libelle=='FORMAT_DATE_INVALIDE')].detectees").value(25))
                .andExpect(jsonPath("$.types[?(@.libelle=='FORMAT_DATE_INVALIDE')].ratees").value(5))
                .andExpect(jsonPath("$.types[?(@.libelle=='FORMAT_DATE_INVALIDE')].fauxPositifs").value(3))
                .andExpect(jsonPath("$.types[?(@.libelle=='FAUTE_ORTHOGRAPHE')].detectees").value(20))
                .andExpect(jsonPath("$.types[?(@.libelle=='FAUTE_ORTHOGRAPHE')].ratees").value(10))
                .andExpect(jsonPath("$.types[?(@.libelle=='FAUTE_ORTHOGRAPHE')].fauxPositifs").value(3))
                .andExpect(jsonPath("$.categories[?(@.libelle=='SEMANTIQUE')].injectees").value(30))
                .andExpect(jsonPath("$.categories[?(@.libelle=='SEMANTIQUE')].detectees").value(20))
                .andExpect(jsonPath("$.categories[?(@.libelle=='STRUCTURELLE')].detectees").value(60));
    }

    @Test
    @DisplayName("Envoi en deux fois (avec un recouvrement) = envoi unique")
    void deuxEnvois() throws Exception {
        Jeu unique = jeuDe100();
        Jeu double_ = jeuDe100();
        List<String> a = detectionsDuJeu(unique);
        envoyer(unique.run(), "v1", a);

        List<String> b = detectionsDuJeu(double_);
        envoyer(double_.run(), "v1", b.subList(0, 50));
        envoyer(double_.run(), "v1", b.subList(40, b.size()));   // 10 detections renvoyees

        assertThat(chiffres(comparaison(double_.run()))).isEqualTo(chiffres(comparaison(unique.run())));
        assertThat(jdbc.queryForObject("select count(*) from quality_report where run_id = ?", Integer.class,
                double_.run())).isEqualTo(2);
    }

    /**
     * Les chiffres seuls : l'identifiant du run differe forcement, et le dernier echange porte son
     * propre horodatage et son propre decompte de detections poussees.
     */
    private static String chiffres(String json) {
        return json.replaceAll("\"runId\":\"[^\"]*\",?", "")
                .replaceAll("\"echange\":\\{[^}]*},?", "");
    }

    @Test
    @DisplayName("Detection sur un evenement inconnu : rejetee en bloc avec un message clair")
    void evenementInconnu() throws Exception {
        Jeu jeu = jeuDe100();
        UUID inconnu = UUID.randomUUID();
        UUID autreRun = evenement(SupportRun.nouveauRun(jdbc));

        mvc.perform(post("/api/runs/{id}/quality/report", jeu.run()).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"v1\",\"detections\":["
                                + detection(jeu.evenements().get(0), "CHAMP_VIDE", "nom") + ","
                                + detection(inconnu, "CHAMP_VIDE", "nom") + ","
                                + detection(autreRun, "CHAMP_VIDE", "nom") + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DETECTION_INVALIDE"))
                .andExpect(jsonPath("$.message", containsString("rien n'a ete enregistre")))
                .andExpect(jsonPath("$.details[0]", containsString("evenement inconnu pour ce run : " + inconnu)))
                .andExpect(jsonPath("$.details[1]", containsString(autreRun.toString())));

        assertThat(jdbc.queryForObject("select count(*) from quality_report where run_id = ?", Integer.class,
                jeu.run())).isZero();
    }

    @Test
    @DisplayName("Run inconnu : 404 ; type inconnu : 400")
    void erreurs() throws Exception {
        mvc.perform(get("/api/runs/{id}/quality/comparison", UUID.randomUUID()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isNotFound());
        Jeu jeu = jeuDe100();
        mvc.perform(post("/api/runs/{id}/quality/report", jeu.run()).header(ApiKeyFilter.ENTETE, CLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionOutil\":\"v1\",\"detections\":["
                                + detection(jeu.evenements().get(0), "N_IMPORTE_QUOI", "nom") + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("type inconnu")));
    }

    @Test
    @DisplayName("Run de reference (0 anomalie) : tout ce qui est detecte est un faux positif")
    void reference() throws Exception {
        UUID run = SupportRun.nouveauRun(jdbc);
        UUID e = evenement(run);
        envoyer(run, "v1", List.of(detection(e, "CHAMP_VIDE", "nom")));

        mvc.perform(get("/api/runs/{id}/quality/comparison", run).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(jsonPath("$.reference").value(true))
                .andExpect(jsonPath("$.total.injectees").value(0))
                .andExpect(jsonPath("$.total.fauxPositifs").value(1))
                .andExpect(jsonPath("$.total.precision").value(0.0));
    }

    @Test
    @DisplayName("Ecran : tableau par type, comparaison de deux runs, export CSV pour un observateur")
    void ecranEtExport() throws Exception {
        Jeu jeu = jeuDe100();
        envoyer(jeu.run(), "v1", detectionsDuJeu(jeu));
        UUID reference = SupportRun.nouveauRun(jdbc);

        mvc.perform(get("/qualite").param("run", jeu.run().toString()).param("comparer", reference.toString())
                        .with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FAUTE_ORTHOGRAPHE")))
                .andExpect(content().string(containsString("Sémantique")));
        mvc.perform(get("/qualite/export.csv").param("run", jeu.run().toString())
                        .with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TOTAL,TOTAL,100,80,20,10,0.8,0.8889")));
    }
}

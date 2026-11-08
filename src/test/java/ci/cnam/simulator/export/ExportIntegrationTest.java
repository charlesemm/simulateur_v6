package ci.cnam.simulator.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.security.ApiKeyFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

/**
 * Export complet d'une simulation terminee, de bout en bout sur un vrai PostgreSQL : les quatre
 * tables, le marquage sur chaque ligne, l'empreinte rejouable, et le corrige qui ne fuit pas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ExportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final String CLE = "test-api-key";

    /** Valeur d'origine du corrige, introuvable ailleurs : si elle apparait dans un fichier, le corrige a fuit. */
    private static final String SECRET = "VALEUR-ORIGINE-DU-CORRIGE-NE-DOIT-PAS-FUIR";

    private static final List<String> TABLES = List.of("assures", "factures", "facture_lignes", "evenements");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    /** Un run termine : 20 assures concernes, 40 factures, 80 lignes, 2000 evenements, 1 anomalie injectee. */
    private record Jeu(UUID run, int assures, int factures, int lignes, int evenements) {

        int lignesTotales() {
            return assures + factures + lignes + evenements;
        }
    }

    private Jeu jeuTermine(int evenements) {
        UUID run = run("ARRETE");
        List<UUID> assures = jdbc.queryForList("select id from assure order by numero_assure limit 20", UUID.class);
        UUID centre = jdbc.queryForObject("select id from centre_sante order by code limit 1", UUID.class);

        List<UUID> factures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            UUID facture = UUID.randomUUID();
            UUID assure = assures.get(i % assures.size());
            jdbc.update("""
                    insert into facture (id, run_id, numero_facture, assure_id, beneficiaire_id, centre_sante_id,
                                         type_bon, statut, date_ouverture, montant_total, part_cnam, part_assure)
                    values (?, ?, 'FAC-2026-' || lpad(nextval('seq_numero_facture')::text, 6, '0'),
                            ?, ?, ?, 'AMBULATOIRE', 'FINALISEE', timestamp '2026-01-02 08:00:00',
                            1000.00, 800.00, 200.00)""",
                    facture, run, assure, assure, centre);
            factures.add(facture);
            for (int l = 1; l <= 2; l++) {
                jdbc.update("""
                        insert into facture_ligne (id, run_id, facture_id, numero_ligne, code, designation, date_soin,
                                                   quantite, prix_unitaire, montant, part_cnam, part_assure)
                        values (?, ?, ?, ?, 'ACT-01', 'Consultation generale',
                                timestamp '2026-01-02 08:30:00', 1, 500.00, 500.00, 400.00, 100.00)""",
                        UUID.randomUUID(), run, facture, l);
            }
        }

        List<UUID> ids = new ArrayList<>();
        jdbc.batchUpdate("""
                insert into evenement (event_id, run_id, facture_id, domaine, type, timestamp_simule, timestamp_reel,
                                       payload)
                values (?, ?, ?, 'prestations', 'FACTURE_OUVERTE', timestamp '2026-01-02 08:00:00',
                        timestamptz '2026-01-02 08:00:00+00', ?::jsonb)""",
                java.util.stream.IntStream.range(0, evenements)
                        .mapToObj(i -> {
                            UUID id = UUID.randomUUID();
                            ids.add(id);
                            return new Object[]{id, run, factures.get(i % factures.size()),
                                    "{\"rang\":" + i + "}"};
                        })
                        .toList());

        jdbc.update("""
                insert into anomaly_injected (run_id, event_id, type, champ, valeur_origine, valeur_corrompue)
                values (?, ?, 'CHAMP_VIDE', 'nom', ?, '')""", run, ids.get(0), SECRET);

        return new Jeu(run, 20, 40, 80, evenements);
    }

    private UUID run(String statut) {
        UUID scenario = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        jdbc.update("insert into scenario (id, nom, contenu_yaml) values (?, ?, 'nom: test')", scenario,
                "export-" + scenario);
        jdbc.update("""
                insert into simulation_run (id, scenario_id, seed, statut, debut_simule, parametres)
                values (?, ?, 42, ?, timestamp '2026-01-02 00:00:00', '{}'::jsonb)""", run, scenario, statut);
        return run;
    }

    /** Un export part en flux : MockMvc demande donc le second temps de la requete asynchrone. */
    private byte[] telecharger(UUID run, String extension) throws Exception {
        MvcResult debut = mvc.perform(get("/api/runs/{id}/export.{ext}", run, extension)
                        .header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(debut)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String telechargerEcran(UUID run, String chemin, org.springframework.test.web.servlet.request
            .RequestPostProcessor compte) throws Exception {
        MvcResult debut = mvc.perform(get(chemin).param("run", run.toString()).with(compte))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(debut)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Les .csv d'une archive zip, par nom d'entree, dans l'ordre de l'archive. */
    private static Map<String, String> entrees(byte[] zip) throws Exception {
        Map<String, String> fichiers = new LinkedHashMap<>();
        try (ZipInputStream flux = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entree;
            while ((entree = flux.getNextEntry()) != null) {
                fichiers.put(entree.getName(), new String(flux.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return fichiers;
    }

    @Test
    @DisplayName("2000 evenements : les 4 tables dans les 3 formats, le bon compte, chaque ligne marquee OUI")
    void troisFormats() throws Exception {
        Jeu jeu = jeuTermine(2_000);

        // --- csv (archive zip) : un fichier par table, BOM, en-tete, puis les lignes
        Map<String, String> csv = entrees(telecharger(jeu.run(), "csv"));
        assertThat(csv.keySet()).containsExactly("assures.csv", "factures.csv", "facture_lignes.csv",
                "evenements.csv");
        Map<String, Integer> attendu = Map.of("assures", jeu.assures(), "factures", jeu.factures(),
                "facture_lignes", jeu.lignes(), "evenements", jeu.evenements());
        for (String table : TABLES) {
            List<String> lignes = csv.get(table + ".csv").lines().toList();
            assertThat(lignes.get(0)).as(table + " : BOM puis en-tete marque")
                    .startsWith("﻿donnee_fictive,simulation,");
            assertThat(lignes).as(table + " : en-tete + lignes").hasSize(attendu.get(table) + 1);
            assertThat(lignes.subList(1, lignes.size())).as(table + " : chaque ligne marquee")
                    .allMatch(l -> l.startsWith("OUI," + jeu.run() + ","));
        }
        assertThat(csv.get("evenements.csv").lines().toList().get(0))
                .as("la cle du rapport qualite est event_id").contains(",event_id,");

        // --- json : un tableau par table, chaque objet marque
        JsonNode racine = json.readTree(telecharger(jeu.run(), "json"));
        assertThat(racine.fieldNames()).toIterable().containsExactlyElementsOf(TABLES);
        for (String table : TABLES) {
            JsonNode tableau = racine.get(table);
            assertThat(tableau).as(table).hasSize(attendu.get(table));
            for (JsonNode ligne : tableau) {
                assertThat(ligne.get("donnee_fictive").asText()).isEqualTo("OUI");
                assertThat(ligne.get("simulation").asText()).isEqualTo(jeu.run().toString());
            }
        }
        assertThat(racine.get("evenements").get(0).has("event_id")).isTrue();

        // --- xlsx : un onglet par table
        byte[] xlsx = telecharger(jeu.run(), "xlsx");
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(classeur.getNumberOfSheets()).isEqualTo(4);
            for (int i = 0; i < TABLES.size(); i++) {
                Sheet feuille = classeur.getSheetAt(i);
                assertThat(feuille.getSheetName()).isEqualTo(TABLES.get(i));
                Row entete = feuille.getRow(0);
                assertThat(entete.getCell(0).getStringCellValue()).isEqualTo("donnee_fictive");
                assertThat(entete.getCell(1).getStringCellValue()).isEqualTo("simulation");
                assertThat(feuille.getLastRowNum()).as(feuille.getSheetName())
                        .isEqualTo(attendu.get(TABLES.get(i)));
                for (int l = 1; l <= feuille.getLastRowNum(); l++) {
                    assertThat(feuille.getRow(l).getCell(0).getStringCellValue()).isEqualTo("OUI");
                    assertThat(feuille.getRow(l).getCell(1).getStringCellValue())
                            .isEqualTo(jeu.run().toString());
                }
            }
        }

        // le nombre de lignes inscrit dans export_fichier est celui des quatre tables reunies
        assertThat(jdbc.queryForList("select nombre_lignes from export_fichier where run_id = ?", Long.class,
                jeu.run())).containsOnly((long) jeu.lignesTotales());
    }

    @Test
    @DisplayName("Deux exports du meme run dans le meme format : la meme empreinte, deux lignes de trace")
    void empreinteRejouable() throws Exception {
        Jeu jeu = jeuTermine(300);
        for (String extension : List.of("csv", "json", "xlsx")) {
            byte[] premier = telecharger(jeu.run(), extension);
            byte[] second = telecharger(jeu.run(), extension);
            assertThat(second).as("octet pour octet : " + extension).isEqualTo(premier);

            String format = extension.toUpperCase(java.util.Locale.ROOT);
            List<String> empreintes = jdbc.queryForList("""
                    select empreinte from export_fichier where run_id = ? and format = ?""",
                    String.class, jeu.run(), format);
            assertThat(empreintes).as("une ligne par telechargement : " + extension).hasSize(2);
            assertThat(empreintes.get(0).strip()).isEqualTo(empreintes.get(1).strip());
            assertThat(empreintes.get(0).strip()).matches("[0-9a-f]{64}");
        }
    }

    @Test
    @DisplayName("Le corrige n'apparait dans AUCUN des trois formats")
    void corrigeNeFuitPas() throws Exception {
        Jeu jeu = jeuTermine(50);
        byte[] secret = SECRET.getBytes(StandardCharsets.UTF_8);

        assertThat(new String(telecharger(jeu.run(), "json"), StandardCharsets.UTF_8)).doesNotContain(SECRET);
        assertThat(entrees(telecharger(jeu.run(), "csv")).values()).noneMatch(c -> c.contains(SECRET));
        // le xlsx est une archive : on cherche dans chaque partie decompressee, pas seulement dans les octets
        java.nio.file.Path fichier = java.nio.file.Files.createTempFile("export", ".xlsx");
        try {
            java.nio.file.Files.write(fichier, telecharger(jeu.run(), "xlsx"));
            try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(fichier.toFile())) {
                for (ZipEntry entree : archive.stream().toList()) {
                    try (var partie = archive.getInputStream(entree)) {
                        assertThat(new String(partie.readAllBytes(), StandardCharsets.UTF_8))
                                .as(entree.getName()).doesNotContain(SECRET);
                    }
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(fichier);
        }
        // ... mais il est bien dans le corrige, servi a part
        String corrige = telechargerEcran(jeu.run(), "/export/corrige.csv", user("op").roles("OPERATEUR"));
        assertThat(corrige).contains(SECRET).contains("valeur_origine").contains("OUI," + jeu.run());
        assertThat(secret).isNotEmpty();
    }

    @Test
    @DisplayName("Corrige : refuse a un OBSERVATEUR et a la cle d'API, accorde a un ADMIN")
    void corrigeReserve() throws Exception {
        Jeu jeu = jeuTermine(10);
        mvc.perform(get("/export/corrige.csv").param("run", jeu.run().toString())
                        .with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isForbidden());
        assertThat(telechargerEcran(jeu.run(), "/export/corrige.csv", user("chef").roles("ADMIN")))
                .startsWith("﻿donnee_fictive,simulation,id,event_id,");
        // la cle d'API ne porte que ROLE_API : aucun chemin ne lui donne le corrige
        mvc.perform(get("/api/runs/{id}/export.corrige", jeu.run()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Simulation en cours : export refuse en 409 RUN_NON_TERMINE, sans trace de telechargement")
    void runEnCours() throws Exception {
        UUID run = run("EN_COURS");
        for (String extension : List.of("csv", "json", "xlsx")) {
            mvc.perform(get("/api/runs/{id}/export.{ext}", run, extension).header(ApiKeyFilter.ENTETE, CLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RUN_NON_TERMINE"));
        }
        mvc.perform(get("/export/jeu.csv").param("run", run.toString()).with(user("chef").roles("ADMIN")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from export_fichier where run_id = ?", Integer.class, run))
                .isZero();
    }

    @Test
    @DisplayName("Run inconnu : 404")
    void runInconnu() throws Exception {
        mvc.perform(get("/api/runs/{id}/export.json", UUID.randomUUID()).header(ApiKeyFilter.ENTETE, CLE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_INCONNU"));
    }

    @Test
    @DisplayName("Ecran : les trois boutons, le corrige selon le role, et la MEME empreinte deux fois")
    void ecran() throws Exception {
        Jeu jeu = jeuTermine(20);
        telecharger(jeu.run(), "csv");
        telecharger(jeu.run(), "csv");

        String page = mvc.perform(get("/export").param("run", jeu.run().toString())
                        .with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("/export/jeu.xlsx?run=" + jeu.run())
                .contains("/export/jeu.csv?run=" + jeu.run())
                .contains("/export/jeu.json?run=" + jeu.run())
                .contains("/export/corrige.csv?run=" + jeu.run())
                .contains("donnee_fictive = OUI");

        String empreinte = jdbc.queryForList("""
                select empreinte from export_fichier where run_id = ? and format = 'CSV'""",
                String.class, jeu.run()).get(0).strip();
        assertThat(page.split(empreinte, -1)).as("l'empreinte est affichee pour les deux telechargements")
                .hasSize(3);

        // un observateur voit le jeu, jamais le corrige
        assertThat(mvc.perform(get("/export").param("run", jeu.run().toString())
                        .with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("/export/jeu.xlsx").doesNotContain("corrige.csv");
    }

    @Test
    @DisplayName("Les assures exportes sont ceux du run, jamais les 500 du referentiel")
    void assuresDuRunSeulement() throws Exception {
        Jeu jeu = jeuTermine(10);
        JsonNode racine = json.readTree(telecharger(jeu.run(), "json"));
        assertThat(racine.get("assures")).hasSize(20);
        assertThat(jdbc.queryForObject("select count(*) from assure", Integer.class)).isGreaterThan(100);
    }
}

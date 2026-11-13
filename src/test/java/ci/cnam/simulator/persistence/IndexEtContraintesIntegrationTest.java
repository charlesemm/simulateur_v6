package ci.cnam.simulator.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.SupportRun;
import ci.cnam.simulator.core.event.StatutEcriture;
import ci.cnam.simulator.domain.FlowGenerator;
import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Index de V21 et contraintes de V22, sur la base montee par l'application.
 *
 * <p>Le test d'alignement est celui qui protege l'avenir : un type d'evenement, un statut ou un
 * domaine ajoute dans le code sans migration le fait echouer, au lieu de faire refuser en silence
 * les lots du DatabaseSink en production.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IndexEtContraintesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final Pattern LITTERAL = Pattern.compile("'([^']*)'");

    @Autowired JdbcTemplate jdbc;
    @Autowired List<FlowGenerator> generateurs;

    // ---------------------------------------------------------------- index (V21)

    @Test
    void laRequeteDuCatalogueNeRenvoiePlusAucuneCleEtrangereSansIndex() throws IOException {
        String requete = Files.readString(Path.of("scripts/db/cles-sans-index.sql"));

        assertThat(jdbc.queryForList(requete)).isEmpty();
        // La requete voit bien les cles etrangeres (sinon elle serait vide pour une autre raison).
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_constraint
                where contype = 'f' and connamespace = 'public'::regnamespace""", Long.class))
                .isGreaterThan(30);
    }

    @Test
    void laLectureDesEvenementsDUnRunUtiliseLIndex() {
        // Plusieurs runs, pour que le run lu ne soit qu'une partie de la table, comme en vrai.
        List<UUID> runs = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            UUID run = SupportRun.nouveauRun(jdbc);
            runs.add(run);
            ecrireEvenements(run, 3000);
        }
        jdbc.execute("analyze \"TB_ECHO_EVENEMENT\"");
        UUID lu = runs.get(1);

        // La requete de EvenementRepository.rechercher, page 0 puis une page lointaine.
        String page = "select event_id, run_id, facture_id, domaine, type, timestamp_simule, timestamp_reel, statut,"
                + " payload from \"TB_ECHO_EVENEMENT\" where run_id = '" + lu + "'"
                + " order by timestamp_reel, event_id limit 50 offset ";
        assertThat(plan(page + "0")).contains("idx_evenement_run_chrono").doesNotContain("Sort");
        // Page lointaine d'un petit run : le planificateur peut preferer trier 3000 lignes, mais il les
        // trouve par l'index du run, jamais en parcourant la table (sur 100 000, il prend l'index : journal).
        assertThat(plan(page + "2000")).contains("idx_evenement_run").doesNotContain("Seq Scan");
        // Le compte de la meme page, et le filtre par type de l'explorateur.
        assertThat(plan("select count(*) from \"TB_ECHO_EVENEMENT\" where run_id = '" + lu + "'"))
                .contains("idx_evenement_run").doesNotContain("Seq Scan");
        assertThat(plan("select count(*) from \"TB_ECHO_EVENEMENT\" where run_id = '" + lu
                + "' and type = 'FACTURE_OUVERTE'")).contains("idx_evenement_run_type");
    }

    // ---------------------------------------------------------------- contraintes (V22)

    @Test
    void lesEnumsEtLesCheckDisentLaMemeChose() {
        assertThat(valeursDuCheck("ck_evenement_statut"))
                .isEqualTo(noms(Arrays.stream(StatutEcriture.values()).map(Enum::name)));

        Set<String> types = noms(generateurs.stream().flatMap(g -> g.typesEvenements().stream()));
        assertThat(valeursDuCheck("ck_evenement_type")).isEqualTo(types);
        assertThat(types).isEqualTo(noms(Arrays.stream(PrestationEventType.values()).map(Enum::name)));

        assertThat(valeursDuCheck("ck_evenement_domaine"))
                .isEqualTo(noms(generateurs.stream().map(FlowGenerator::domaine)));
    }

    @Test
    void unEvenementDeStatutInconnuEstRefuse() {
        UUID run = SupportRun.nouveauRun(jdbc);

        assertThatThrownBy(() -> inserer(run, "prestations", "FACTURE_OUVERTE", "PERDU"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_evenement_statut");
        // Le rejeu remet a ECRIT un evenement ECHOUE : les deux valeurs passent.
        inserer(run, "prestations", "FACTURE_OUVERTE", "ECHOUE");
        inserer(run, "prestations", "FACTURE_OUVERTE", "ECRIT");
    }

    @Test
    void unEvenementDeTypeOuDeDomaineInconnuEstRefuse() {
        UUID run = SupportRun.nouveauRun(jdbc);

        assertThatThrownBy(() -> inserer(run, "prestations", "FACTURE_PERDUE", "ECRIT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_evenement_type");
        // La casse compte : l'application ecrit le nom exact de l'enum et du domaine.
        assertThatThrownBy(() -> inserer(run, "prestations", "facture_ouverte", "ECRIT"))
                .hasMessageContaining("ck_evenement_type");
        assertThatThrownBy(() -> inserer(run, "PRESTATIONS", "FACTURE_OUVERTE", "ECRIT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_evenement_domaine");
    }

    @Test
    void lignesEtHistoriqueOntUneCleEtrangereVersLeRunEnCascade() {
        assertThat(jdbc.queryForList("""
                select conrelid::regclass::text || ' ' || conname || ' ' || confdeltype::text
                from pg_constraint
                where conname in ('fk_facture_ligne_run', 'fk_facture_historique_run')
                  and confrelid = '"TB_ECHO_SIMULATION_RUN"'::regclass
                order by 1""", String.class))
                .containsExactly("\"TB_ECHO_FACTURE_HISTORIQUE\" fk_facture_historique_run c",
                        "\"TB_ECHO_FACTURE_LIGNE\" fk_facture_ligne_run c");
    }

    @Test
    void leControleAvantV22NeRenvoieRienSurUneBaseSaine() throws IOException {
        UUID run = SupportRun.nouveauRun(jdbc);
        ecrireEvenements(run, 10);

        assertThat(jdbc.queryForList(Files.readString(Path.of("scripts/db/controle-avant-v22.sql")))).isEmpty();
    }

    // ---------------------------------------------------------------- outils

    private Set<String> valeursDuCheck(String contrainte) {
        String definition = jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?", String.class, contrainte);
        assertThat(definition).as(contrainte).isNotNull();
        Set<String> valeurs = new TreeSet<>();
        Matcher m = LITTERAL.matcher(definition);
        while (m.find()) {
            valeurs.add(m.group(1));
        }
        return valeurs;
    }

    private static Set<String> noms(java.util.stream.Stream<String> flux) {
        return flux.collect(Collectors.toCollection(TreeSet::new));
    }

    private String plan(String requete) {
        return String.join("\n", jdbc.queryForList("explain " + requete, String.class));
    }

    private void inserer(UUID run, String domaine, String type, String statut) {
        jdbc.update("""
                insert into "TB_ECHO_EVENEMENT" (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel,
                                                 payload, statut)
                values (?, ?, ?, ?, now(), now(), '{}'::jsonb, ?)""", UUID.randomUUID(), run, domaine, type, statut);
    }

    private void ecrireEvenements(UUID run, int nombre) {
        Instant debut = Instant.parse("2026-03-10T08:00:00Z");
        jdbc.batchUpdate("""
                insert into "TB_ECHO_EVENEMENT" (event_id, run_id, domaine, type, timestamp_simule, timestamp_reel,
                                                 payload)
                values (?, ?, 'prestations', ?, now(), ?, '{}'::jsonb)""",
                java.util.stream.IntStream.range(0, nombre).mapToObj(i -> new Object[]{
                        UUID.randomUUID(), run,
                        PrestationEventType.values()[i % PrestationEventType.values().length].name(),
                        Timestamp.from(debut.plusMillis(i))}).toList());
    }
}

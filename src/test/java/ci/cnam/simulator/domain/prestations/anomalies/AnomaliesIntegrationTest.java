package ci.cnam.simulator.domain.prestations.anomalies;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.SupportRun;
import ci.cnam.simulator.anomaly.AnomalyInjector;
import ci.cnam.simulator.anomaly.AnomalyQueryService;
import ci.cnam.simulator.anomaly.EvenementsDeTest;
import ci.cnam.simulator.anomaly.InjectionSink;
import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.sink.DatabaseSink;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** La verite terrain sur un vrai PostgreSQL : chaque ligne d'anomaly_injected retrouve la valeur propre d'origine. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AnomaliesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"));

    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseSink sink;
    @Autowired AnomalyInjector injecteur;
    @Autowired AnomalyQueryService service;
    @Autowired ObjectMapper json;
    @Autowired RunManager manager;
    @Autowired ScenarioLoader loader;

    private void ecrire(UUID run, List<SimEvent> propres, AnomalyProfile profil) {
        InjectionSink injection = new InjectionSink(sink, injecteur, profil);
        propres.forEach(injection::emettre);
        injection.flush();
    }

    @Test
    void chaqueLigneRetrouveLaValeurProprePuisLeDoublonReferenceSonOriginal() throws Exception {
        UUID run = SupportRun.nouveauRun(jdbc);
        List<SimEvent> propres = EvenementsDeTest.propres(run, 42, 3_000);
        Map<UUID, SimEvent> parId = new HashMap<>();
        propres.forEach(e -> parId.put(e.eventId(), e));
        AnomalyProfile profil = AnomalyProfile.depuis(Map.of("global", 20.0));
        long attendues = propres.stream().flatMap(e -> injecteur.injecter(e, profil).stream())
                .mapToLong(e -> e.anomalies().size()).sum();
        long doublons = propres.stream().flatMap(e -> injecteur.injecter(e, profil).stream())
                .filter(e -> !e.anomalies().isEmpty() && e.anomalies().get(0).type().equals("DOUBLON_EXACT")).count();

        ecrire(run, propres, profil);

        List<Map<String, Object>> lignes = jdbc.queryForList("""
                select a.event_id, a.facture_id, a.type, a.champ, a.valeur_origine, a.valeur_corrompue,
                       a.doublon_de, e.payload::text as payload
                from anomaly_injected a join evenement e on e.event_id = a.event_id
                where a.run_id = ?""", run);
        assertThat(attendues).isPositive();
        assertThat(lignes).hasSize((int) attendues);
        assertThat(jdbc.queryForObject("select count(*) from evenement where run_id = ?", Long.class, run))
                .isEqualTo(propres.size() + doublons);

        int doublonsVus = 0;
        for (Map<String, Object> l : lignes) {
            Map<String, Object> payload = json.readValue((String) l.get("payload"), PAYLOAD);
            if (l.get("type").equals("DOUBLON_EXACT")) {
                doublonsVus++;
                SimEvent original = parId.get((UUID) l.get("doublon_de"));
                assertThat(original).isNotNull();
                assertThat((UUID) l.get("event_id")).isNotEqualTo(original.eventId());
                assertThat(l.get("champ")).isNull();
                assertThat(payload).isEqualTo(json.readValue(json.writeValueAsString(original.payload()), PAYLOAD));
                assertThat(jdbc.queryForObject("select count(*) from evenement where event_id = ?", Long.class,
                        original.eventId())).isEqualTo(1);
                continue;
            }
            SimEvent propre = parId.get((UUID) l.get("event_id"));
            String champ = (String) l.get("champ");
            assertThat(l.get("valeur_origine")).isEqualTo(String.valueOf(propre.payload().get(champ)));
            Object ecrite = payload.get(champ);
            assertThat(l.get("valeur_corrompue")).isEqualTo(ecrite == null ? null : ecrite.toString());
            assertThat(l.get("valeur_corrompue")).isNotEqualTo(l.get("valeur_origine"));
            assertThat(l.get("facture_id")).isEqualTo(propre.payload().get("factureId"));
        }
        assertThat(doublonsVus).isEqualTo((int) doublons);
    }

    @Test
    void lAPILitLesFiltresParTypeEtParChampEtPagine() {
        UUID run = SupportRun.nouveauRun(jdbc);
        ecrire(run, EvenementsDeTest.propres(run, 7, 3_000), AnomalyProfile.depuis(Map.of("global", 30.0)));

        long total = jdbc.queryForObject("select count(*) from anomaly_injected where run_id = ?", Long.class, run);
        long champVide = jdbc.queryForObject(
                "select count(*) from anomaly_injected where run_id = ? and type = 'CHAMP_VIDE'", Long.class, run);
        long surNom = jdbc.queryForObject(
                "select count(*) from anomaly_injected where run_id = ? and champ = 'nom'", Long.class, run);

        assertThat(service.rechercher(run, null, null, 0, 10).total()).isEqualTo(total);
        assertThat(service.rechercher(run, null, null, 0, 10).contenu()).hasSize(10);
        assertThat(service.rechercher(run, null, null, 0, 10).totalPages()).isEqualTo((int) ((total + 9) / 10));
        assertThat(service.rechercher(run, "CHAMP_VIDE", null, 0, 500).total()).isEqualTo(champVide);
        assertThat(service.rechercher(run, "champVide", null, 0, 500).contenu())
                .allSatisfy(a -> assertThat(a.type().name()).isEqualTo("CHAMP_VIDE"));
        assertThat(service.rechercher(run, null, "nom", 0, 500).total()).isEqualTo(surNom);
        assertThat(service.rechercher(run, "CHAMP_VIDE", "nom", 0, 500).contenu())
                .allSatisfy(a -> assertThat(a.champ()).isEqualTo("nom"));

        // Pages successives : aucune ligne repetee ni sautee.
        List<UUID> lues = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            service.rechercher(run, null, null, page, 7).contenu().forEach(a -> lues.add(a.eventId()));
        }
        assertThat(lues).hasSize(21).doesNotHaveDuplicates();

        // Supprimer le run supprime sa verite terrain.
        jdbc.update("delete from simulation_run where id = ?", run);
        assertThat(jdbc.queryForObject("select count(*) from anomaly_injected where run_id = ?", Long.class, run))
                .isZero();
    }

    @Test
    void unTauxDeZeroNeLaisseAucuneLigneEnBase() {
        UUID run = SupportRun.nouveauRun(jdbc);
        ecrire(run, EvenementsDeTest.propres(run, 99, 2_000), AnomalyProfile.depuis(Map.of("global", 0.0)));

        assertThat(jdbc.queryForObject("select count(*) from evenement where run_id = ?", Long.class, run))
                .isEqualTo(2_000);
        assertThat(jdbc.queryForObject("select count(*) from anomaly_injected where run_id = ?", Long.class, run))
                .isZero();
    }

    @Test
    void unRunDeBoutEnBoutInjecteSeulementQuandLeScenarioLeDemande() throws Exception {
        // Graines differentes : meme graine = memes identifiants d'evenements, qui s'excluraient entre les deux runs.
        UUID avec = lancer("global: 30%", 42);
        UUID sans = lancer(null, 43);

        assertThat(compter(avec)).isPositive();
        // Chaque anomalie est portee par un evenement ecrit, et la valeur ecrite est bien la valeur corrompue.
        assertThat(jdbc.queryForObject("""
                select count(*) from anomaly_injected a join evenement e on e.event_id = a.event_id
                where a.run_id = ? and a.champ is not null and (e.payload ->> a.champ) is distinct from a.valeur_corrompue
                """, Long.class, avec)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from anomaly_injected
                where run_id = ? and champ is not null and valeur_origine is not distinct from valeur_corrompue
                """, Long.class, avec)).isZero();
        // Un doublon n'a pas de champ et reference un evenement original ecrit dans le meme run.
        assertThat(jdbc.queryForObject("""
                select count(*) from anomaly_injected a join evenement o on o.event_id = a.doublon_de
                where a.run_id = ? and a.type = 'DOUBLON_EXACT' and o.run_id = a.run_id
                """, Long.class, avec)).isEqualTo(jdbc.queryForObject(
                "select count(*) from anomaly_injected where run_id = ? and type = 'DOUBLON_EXACT'", Long.class, avec));
        // L'identite du patient est bien dans les evenements du parcours.
        assertThat(jdbc.queryForObject("""
                select count(*) from evenement where run_id = ? and type = 'PATIENT_ARRIVE'
                  and jsonb_exists(payload, 'nom') and jsonb_exists(payload, 'prenoms')
                  and jsonb_exists(payload, 'dateNaissance') and jsonb_exists(payload, 'genre')
                """, Long.class, avec)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from evenement where run_id = ?", Long.class, sans))
                .isPositive();
        assertThat(compter(sans)).isZero();
    }

    private UUID lancer(String anomalies, int seed) throws Exception {
        String yaml = """
                nom: "IT - anomalies %s"
                seed: %d
                accelerationTemps: 100
                domaines:
                  prestations:
                    debit: 20/s
                %s""".formatted(UUID.randomUUID(), seed, anomalies == null ? ""
                : "    anomalies: { " + anomalies + " }\n");
        RunInfo run = manager.creer(loader.lire(yaml));
        manager.demarrer(run.id());
        Thread.sleep(2_500);
        manager.arreter(run.id());
        return run.id();
    }

    private long compter(UUID run) {
        return jdbc.queryForObject("select count(*) from anomaly_injected where run_id = ?", Long.class, run);
    }
}

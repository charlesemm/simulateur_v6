package ci.cnam.simulator.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Mesure (memoire et duree) d'un export de 100 000 evenements. Ce n'est pas un test de regression :
 * il est long et sert a REPORTER un chiffre dans le journal, pas a garder le build vert.
 *
 * <p>Il ne tourne donc pas dans {@code clean verify}. Pour le lancer, avec le tas d'un conteneur a
 * {@code mem_limit 2g} (soit environ 1 Gio de tas) :
 *
 * <pre>
 * .\mvnw.cmd test -Dtest=ExportMesureTest -Dmesure.export=true `
 *     -DargLine="-Xmx1g -Dmesure.export=true"
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "mesure.export", matches = "true")
class ExportMesureTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withStartupAttempts(3);

    private static final int EVENEMENTS = 100_000;

    @Autowired ExportService export;
    @Autowired JdbcTemplate jdbc;

    /** Flux qui jette tout : on mesure l'export, pas le disque ni le reseau. */
    private static final class Puits extends OutputStream {
        long octets;

        @Override
        public void write(int b) {
            octets++;
        }

        @Override
        public void write(byte[] b, int d, int l) {
            octets += l;
        }
    }

    @Test
    @DisplayName("MESURE : 100 000 evenements exportes en xlsx, csv et json")
    void centMilleEvenements() throws Exception {
        UUID run = preparer();
        System.out.printf("%n=== MESURE EXPORT (%d evenements, tas max %d Mio) ===%n",
                EVENEMENTS, Runtime.getRuntime().maxMemory() / 1_048_576);
        for (FormatExport format : List.of(FormatExport.XLSX, FormatExport.CSV, FormatExport.JSON)) {
            mesurer(run, format);
        }
    }

    private void mesurer(UUID run, FormatExport format) throws Exception {
        Runtime machine = Runtime.getRuntime();
        System.gc();
        Thread.sleep(200);
        long avant = machine.totalMemory() - machine.freeMemory();
        long debut = System.nanoTime();

        Puits puits = new Puits();
        ResultatExport resultat = export.exporter(run, format, puits);

        long duree = (System.nanoTime() - debut) / 1_000_000;
        long pointe = machine.totalMemory() - machine.freeMemory();
        System.gc();
        Thread.sleep(200);
        long apres = machine.totalMemory() - machine.freeMemory();

        System.out.printf("%-5s : %6d ms | %7d lignes | %8.1f Mio ecrits | tas %4d -> %4d Mio "
                        + "(retenu apres GC : %4d Mio) | empreinte %s%n",
                format, duree, resultat.lignes(), puits.octets / 1_048_576.0,
                avant / 1_048_576, pointe / 1_048_576, apres / 1_048_576,
                resultat.empreinte().substring(0, 12));

        assertThat(resultat.lignes()).isGreaterThanOrEqualTo(EVENEMENTS);
        assertThat(puits.octets).isPositive();
    }

    private UUID preparer() {
        UUID scenario = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        jdbc.update("insert into scenario (id, nom, contenu_yaml) values (?, ?, 'nom: mesure')", scenario,
                "mesure-" + scenario);
        jdbc.update("""
                insert into "TB_ECHO_SIMULATION_RUN" (id, scenario_id, seed, statut, debut_simule, parametres)
                values (?, ?, 1, 'ARRETE', timestamp '2026-01-02 00:00:00', '{}'::jsonb)""", run, scenario);

        List<UUID> assures = jdbc.queryForList("select id from \"TB_ECHO_ASSURE\" order by numero_assure limit 200", UUID.class);
        UUID centre = jdbc.queryForObject("select id from \"TB_ECHO_CENTRE_SANTE\" order by code limit 1", UUID.class);
        List<UUID> factures = new ArrayList<>();
        jdbc.batchUpdate("""
                insert into "TB_ECHO_FACTURE" (id, run_id, numero_facture, assure_id, beneficiaire_id, centre_sante_id,
                                     type_bon, statut, date_ouverture, montant_total, part_cnam, part_assure)
                values (?, ?, 'FAC-2026-' || lpad(nextval('seq_numero_facture')::text, 6, '0'), ?, ?, ?,
                        'AMBULATOIRE', 'FINALISEE', timestamp '2026-01-02 08:00:00', 1000.00, 800.00, 200.00)""",
                java.util.stream.IntStream.range(0, 20_000).mapToObj(i -> {
                    UUID facture = UUID.randomUUID();
                    factures.add(facture);
                    UUID assure = assures.get(i % assures.size());
                    return new Object[]{facture, run, assure, assure, centre};
                }).toList());
        jdbc.batchUpdate("""
                insert into "TB_ECHO_FACTURE_LIGNE" (id, run_id, facture_id, numero_ligne, code, designation, date_soin,
                                           quantite, prix_unitaire, montant, part_cnam, part_assure)
                values (?, ?, ?, 1, 'ACT-01', 'Consultation generale', timestamp '2026-01-02 08:30:00',
                        1, 1000.00, 1000.00, 800.00, 200.00)""",
                factures.stream().map(f -> new Object[]{UUID.randomUUID(), run, f}).toList());
        jdbc.batchUpdate("""
                insert into "TB_ECHO_EVENEMENT" (event_id, run_id, facture_id, domaine, type, timestamp_simule,
                                       timestamp_reel, payload)
                values (?, ?, ?, 'prestations', 'FACTURE_OUVERTE', timestamp '2026-01-02 08:00:00',
                        timestamptz '2026-01-02 08:00:00+00', ?::jsonb)""",
                java.util.stream.IntStream.range(0, EVENEMENTS).mapToObj(i -> new Object[]{
                        UUID.randomUUID(), run, factures.get(i % factures.size()),
                        "{\"rang\":" + i + ",\"libelle\":\"consultation generale de medecine\"}"}).toList());
        return run;
    }
}

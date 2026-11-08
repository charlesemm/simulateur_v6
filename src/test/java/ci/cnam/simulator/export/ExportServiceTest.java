package ci.cnam.simulator.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunStore;
import ci.cnam.simulator.core.scenario.Scenario;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le marquage et le refus d'un run en cours, sans base : les regles qui ne doivent dependre d'aucun
 * format ni d'aucun reglage.
 */
class ExportServiceTest {

    private static final UUID RUN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** Ecrivain d'essai : il garde ce qu'on lui donne, en-tetes et lignes. */
    private static final class EcrivainEspion implements EcrivainExport {
        final List<String> entetes = new ArrayList<>();
        final List<List<String>> lignes = new ArrayList<>();

        @Override
        public FormatExport format() {
            return FormatExport.JSON;
        }

        @Override
        public void ecrire(List<TableExport> tables, OutputStream sortie) throws IOException {
            for (TableExport table : tables) {
                entetes.add(table.nom() + " = " + String.join(",", table.colonnes()));
                table.lignes().pour(lignes::add);
            }
            sortie.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Store d'essai : deux tables de deux lignes, et la trace des telechargements. */
    private static final class StoreEnMemoire implements JeuStore {
        final List<Telechargement> traces = new ArrayList<>();
        final List<ci.cnam.simulator.anomaly.AnomalyRecord> corrige = new ArrayList<>();

        private static TableExport table(String nom, String prefixe) {
            return new TableExport(nom, List.of("id", "valeur"),
                    ligne -> {
                        ligne.accept(java.util.Arrays.asList(prefixe + "-1", "a"));
                        ligne.accept(java.util.Arrays.asList(prefixe + "-2", null));
                    });
        }

        @Override
        public TableExport assures(UUID runId) {
            return table("assures", "ass");
        }

        @Override
        public TableExport factures(UUID runId) {
            return table("factures", "fac");
        }

        @Override
        public TableExport factureLignes(UUID runId) {
            return table("facture_lignes", "lig");
        }

        @Override
        public TableExport evenements(UUID runId) {
            return table("evenements", "evt");
        }

        @Override
        public TableExport corrige(UUID runId) {
            return table("corrige", "cor");
        }

        @Override
        public void tracer(UUID id, UUID runId, FormatExport format, long nombreLignes, String empreinte) {
            traces.add(new Telechargement(id, format, Instant.EPOCH, nombreLignes, empreinte));
        }

        @Override
        public void tracerAnomalies(List<ci.cnam.simulator.anomaly.AnomalyRecord> anomalies) {
            corrige.addAll(anomalies);
        }

        @Override
        public List<Telechargement> telechargements(UUID runId) {
            return List.copyOf(traces);
        }
    }

    private static RunStore runs(EtatRun etat) {
        return new RunStore() {
            @Override
            public void creer(UUID runId, Scenario s) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void mettreAJour(UUID runId, EtatRun e, Instant debut, Instant fin, String erreur) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<RunInfo> lister() {
                return List.of();
            }

            @Override
            public Optional<RunInfo> trouver(UUID runId) {
                return RUN.equals(runId)
                        ? Optional.of(new RunInfo(RUN, "s", 1L, etat, 0, 0, null, null, null))
                        : Optional.empty();
            }
        };
    }

    private record Montage(ExportService service, EcrivainEspion espion, StoreEnMemoire store) {
    }

    private static Montage montage(EtatRun etat) {
        StoreEnMemoire store = new StoreEnMemoire();
        EcrivainEspion espion = new EcrivainEspion();
        ExportService service = new ExportService(new JeuSimulation(store), store, runs(etat),
                new InjecteurFichier(List.of()), run -> java.util.Map.of(), List.of(espion));
        return new Montage(service, espion, store);
    }

    @Test
    @DisplayName("Chaque ligne de chaque table porte donnee_fictive = OUI et la reference du run")
    void marquageSystematique() throws IOException {
        Montage m = montage(EtatRun.ARRETE);
        ResultatExport resultat = m.service().exporter(RUN, FormatExport.JSON, new ByteArrayOutputStream());

        assertThat(m.espion().entetes).containsExactly(
                "assures = donnee_fictive,simulation,id,valeur",
                "factures = donnee_fictive,simulation,id,valeur",
                "facture_lignes = donnee_fictive,simulation,id,valeur",
                "evenements = donnee_fictive,simulation,id,valeur");
        assertThat(m.espion().lignes).hasSize(8)
                .allSatisfy(l -> assertThat(l.subList(0, 2)).containsExactly("OUI", RUN.toString()));
        assertThat(resultat.lignes()).isEqualTo(8);
        assertThat(resultat.empreinte()).matches("[0-9a-f]{64}");
        assertThat(m.store().traces).hasSize(1);
        assertThat(m.store().traces.get(0).lignes()).isEqualTo(8);
    }

    @Test
    @DisplayName("L'empreinte est celle des octets envoyes")
    void empreinteDesOctetsEnvoyes() throws IOException {
        Montage m = montage(EtatRun.ARRETE);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ResultatExport resultat = m.service().exporter(RUN, FormatExport.JSON, sortie);

        assertThat(sortie.toByteArray()).isEqualTo("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // SHA-256 de "x"
        assertThat(resultat.empreinte())
                .isEqualTo("2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881");
    }

    @Test
    @DisplayName("Le corrige est servi seul, et lui aussi marque")
    void corrigeSeul() throws IOException {
        Montage m = montage(EtatRun.ARRETE);
        ExportService service = new ExportService(new JeuSimulation(m.store()), m.store(), runs(EtatRun.ARRETE),
                new InjecteurFichier(List.of()), run -> java.util.Map.of(),
                List.of(new EcrivainCorrigeCsv()));
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ResultatExport resultat = service.exporterCorrige(RUN, sortie);

        String csv = sortie.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿donnee_fictive,simulation,id,valeur\r\n")
                .contains("OUI," + RUN + ",cor-1,a");
        assertThat(resultat.lignes()).isEqualTo(2);
        assertThat(m.store().traces.get(0).format()).isEqualTo(FormatExport.CORRIGE);
    }

    @Test
    @DisplayName("Run en cours, en pause ou juste cree : export refuse, aucune trace")
    void runNonTermine() {
        for (EtatRun etat : List.of(EtatRun.CREE, EtatRun.EN_COURS, EtatRun.EN_PAUSE)) {
            Montage m = montage(etat);
            assertThatThrownBy(() -> m.service().exporter(RUN, FormatExport.JSON, new ByteArrayOutputStream()))
                    .isInstanceOf(ExportIndisponibleException.class)
                    .hasMessageContaining("n'est pas terminee");
            assertThatThrownBy(() -> m.service().exporterCorrige(RUN, new ByteArrayOutputStream()))
                    .isInstanceOf(ExportIndisponibleException.class);
            assertThat(m.store().traces).isEmpty();
        }
    }

    @Test
    @DisplayName("Run echoue : termine, donc exportable (il n'ecrit plus)")
    void runEchoue() throws IOException {
        Montage m = montage(EtatRun.ECHOUE);
        assertThat(m.service().exporter(RUN, FormatExport.JSON, new ByteArrayOutputStream()).lignes()).isEqualTo(8);
    }

    @Test
    @DisplayName("Run inconnu : refus en amont du flux")
    void runInconnu() {
        Montage m = montage(EtatRun.ARRETE);
        assertThatThrownBy(() -> m.service().exporter(UUID.randomUUID(), FormatExport.JSON,
                new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Run inconnu");
    }

    @Test
    @DisplayName("Nom du fichier : le jeu et le corrige ne se confondent pas")
    void nomsDeFichiers() {
        assertThat(FormatExport.CSV.nomFichier(RUN)).isEqualTo("simulation-11111111.zip");
        assertThat(FormatExport.XLSX.nomFichier(RUN)).isEqualTo("simulation-11111111.xlsx");
        assertThat(FormatExport.JSON.nomFichier(RUN)).isEqualTo("simulation-11111111.json");
        assertThat(FormatExport.CORRIGE.nomFichier(RUN)).isEqualTo("corrige-11111111.csv");
        assertThat(FormatExport.duJeu("corrige")).isEmpty();
        assertThat(FormatExport.duJeu("XLSX")).contains(FormatExport.XLSX);
    }
}

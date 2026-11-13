package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.chaos.ChaosType;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.export.FormatExport;
import ci.cnam.simulator.export.Telechargement;
import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.EtatEchange;
import ci.cnam.simulator.quality.LigneQualite;
import ci.cnam.simulator.quality.QualityReportService;
import ci.cnam.simulator.quality.StatutEchange;
import ci.cnam.simulator.web.SimulationWebService.Fiche;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La fiche d'une simulation est un ASSEMBLAGE : chaque element vient tel quel du service qui en repond.
 * Les services sont simules pour le prouver — ce que la fiche porte est l'objet meme qu'ils ont rendu.
 */
class SimulationWebServiceTest {

    private static final UUID RUN = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final Instant DEBUT = Instant.parse("2026-11-08T01:00:00Z");
    private static final Instant MAINTENANT = Instant.parse("2026-11-08T10:00:00Z");

    private final RunManager runs = mock(RunManager.class);
    private final ChaosInjector chaos = mock(ChaosInjector.class);
    private final QualityReportService qualite = mock(QualityReportService.class);
    private final ExportService export = mock(ExportService.class);
    private final SimulationWebService service = new SimulationWebService(runs, chaos, qualite, export,
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private static RunInfo run(EtatRun etat, Instant debut, Instant fin) {
        return new RunInfo(RUN, "parcours-normal", 42L, etat, 12, 120, debut, fin, null);
    }

    private static ChaosAction panne(ChaosType type, String debut) {
        Instant t = Instant.parse(debut);
        return new ChaosAction(UUID.randomUUID(), RUN, type, Map.of(), t, t.plusSeconds(30), t.plusSeconds(30),
                "EXPIREE", "op", 3);
    }

    private static Telechargement fichier(FormatExport format) {
        return new Telechargement(UUID.randomUUID(), format, MAINTENANT, 1200, "abc123");
    }

    private void seulRun(RunInfo run) {
        when(runs.lister()).thenReturn(List.of(run));
        when(chaos.actions(RUN)).thenReturn(List.of());
        when(export.telechargements(RUN)).thenReturn(List.of());
    }

    @Test
    @DisplayName("La fiche assemble le run, la duree, les pannes, le rapport et les telechargements, sans rien calculer")
    void assemblage() {
        RunInfo run = run(EtatRun.ARRETE, DEBUT, DEBUT.plusSeconds(725));
        ChaosAction premiere = panne(ChaosType.LATENCE, "2026-11-08T01:02:00Z");
        ChaosAction seconde = panne(ChaosType.SURCHARGE, "2026-11-08T01:06:00Z");
        ComparaisonQualite rapport = new ComparaisonQualite(RUN, null, false,
                new EtatEchange(StatutEchange.RAPPORT_RECU, MAINTENANT, "rapport recu"),
                LigneQualite.de("TOTAL", 100, 60, 5), List.of(), List.of());
        when(runs.lister()).thenReturn(List.of(run));
        when(chaos.actions(RUN)).thenReturn(List.of(premiere, seconde));
        when(qualite.comparer(RUN, "")).thenReturn(rapport);
        when(export.telechargements(RUN)).thenReturn(List.of(fichier(FormatExport.XLSX), fichier(FormatExport.JSON)));

        Fiche fiche = service.fiche(RUN).orElseThrow();

        assertThat(fiche.run()).isSameAs(run);
        assertThat(fiche.duree()).isEqualTo(Duration.ofSeconds(725));
        // Les memes actions, dans l'ordre inverse : la plus recente d'abord.
        assertThat(fiche.pannes()).containsExactly(seconde, premiere);
        // Le rapport est celui du service de qualite, pas une copie recalculee.
        assertThat(fiche.rapport()).isSameAs(rapport);
        assertThat(fiche.telechargements()).isEqualTo(2);

        // Une seule question posee a chaque service, toutes versions de l'outil confondues.
        verify(qualite).comparer(RUN, "");
        verify(chaos).actions(RUN);
        verify(export).telechargements(RUN);
        verifyNoMoreInteractions(qualite, chaos, export);
    }

    @Test
    @DisplayName("Echange echoue : la fiche porte l'absence de mesure telle quelle, jamais un chiffre invente")
    void sansMesure() {
        seulRun(run(EtatRun.ARRETE, DEBUT, DEBUT.plusSeconds(60)));
        ComparaisonQualite echec = ComparaisonQualite.sansMesure(RUN, null,
                new EtatEchange(StatutEchange.SILENCE, MAINTENANT, "silence"));
        when(qualite.comparer(RUN, "")).thenReturn(echec);

        Fiche fiche = service.fiche(RUN).orElseThrow();

        assertThat(fiche.rapport()).isSameAs(echec);
        assertThat(fiche.rapport().mesure()).isFalse();
        assertThat(fiche.pannes()).isEmpty();
        assertThat(fiche.telechargements()).isZero();
    }

    @Test
    @DisplayName("Simulation encore en marche : la duree court jusqu'a l'horloge ; jamais demarree : duree inconnue")
    void dureeSelonLEtat() {
        seulRun(run(EtatRun.EN_COURS, DEBUT, null));
        assertThat(service.fiche(RUN).orElseThrow().duree()).isEqualTo(Duration.between(DEBUT, MAINTENANT));

        seulRun(run(EtatRun.CREE, null, null));
        assertThat(service.fiche(RUN).orElseThrow().duree()).isNull();
    }

    @Test
    @DisplayName("Simulation inconnue : pas de fiche, et aucun service n'est interroge pour elle")
    void inconnue() {
        seulRun(run(EtatRun.ARRETE, DEBUT, DEBUT.plusSeconds(60)));

        assertThat(service.fiche(UUID.randomUUID())).isEmpty();
        verifyNoMoreInteractions(qualite, chaos, export);
    }

    @Test
    @DisplayName("La liste est celle du moteur, dans son ordre")
    void liste() {
        List<RunInfo> duMoteur = List.of(run(EtatRun.ARRETE, DEBUT, DEBUT.plusSeconds(60)));
        when(runs.lister()).thenReturn(duMoteur);

        assertThat(service.toutes()).isSameAs(duMoteur);
    }
}

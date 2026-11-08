package ci.cnam.simulator.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regles de securite du chaos : duree plafonnee, parametres stricts, audit, retrait garanti. */
class ChaosInjectorTest {

    private final UUID run = UUID.randomUUID();
    private ToxiproxyClient toxiproxy;
    private ChaosActionStore store;
    private RunManager runs;
    private LoadMultiplier charge;
    private TroncatureMessages troncature;
    private ChaosInjector chaos;
    private EtatRun etatDuRun;

    @BeforeEach
    void preparer() {
        toxiproxy = mock(ToxiproxyClient.class);
        store = mock(ChaosActionStore.class);
        runs = mock(RunManager.class);
        charge = new LoadMultiplier();
        troncature = new TroncatureMessages();
        etatDuRun = EtatRun.EN_COURS;
        when(runs.info(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (!id.equals(run)) {
                throw new IllegalArgumentException("Run inconnu : " + id);
            }
            return new RunInfo(run, "s", 1, etatDuRun, 0, 0, Instant.now(), null, null);
        });
        SimulateurProperties proprietes = new SimulateurProperties(42, "scenarios", null, null,
                new SimulateurProperties.Ecriture(10, 10, 10, 1, 1000), new SimulateurProperties.Chaos(60, 2000), new SimulateurProperties.Qualite(60, "UTC"));
        chaos = new ChaosInjector(toxiproxy, charge, troncature, store, runs, Clock.systemUTC(), proprietes);
    }

    @AfterEach
    void nettoyer() {
        chaos.arreter();
    }

    @Test
    void uneCoupureDesactiveLeProxyEtLaisseUneTraceDAudit() {
        ChaosAction a = chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(30), Map.of(), "alice");

        verify(store).creer(a);
        verify(toxiproxy).desactiverProxy();
        assertThat(a.auteur()).isEqualTo("alice");
        assertThat(a.parametres()).containsEntry("mode", "DESACTIVER");
        assertThat(Duration.between(a.debut(), a.finPrevue())).isEqualTo(Duration.ofSeconds(30));
        assertThat(chaos.actif(ChaosType.COUPURE_BASE)).isTrue();
    }

    @Test
    void lModeResetPeerReinitialiseLesConnexions() {
        chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(5), Map.of("mode", "RESET_PEER"), "a");

        verify(toxiproxy).ajouterResetPeer();
        verify(toxiproxy, never()).desactiverProxy();
    }

    @Test
    void laLatenceEstAjouteeAvecSonJitter() {
        chaos.declencher(run, ChaosType.LATENCE, Duration.ofSeconds(5), Map.of("latenceMs", 500, "jitterMs", 20), "a");

        verify(toxiproxy).ajouterLatence(500, 20);
    }

    @Test
    void laSurchargeFixeLeMultiplicateur() {
        chaos.declencher(run, ChaosType.SURCHARGE, Duration.ofSeconds(5), Map.of("multiplicateur", 20), "a");

        assertThat(charge.facteur()).isEqualTo(20.0);
    }

    @Test
    void lesMessagesTronquesActiventLaTroncature() {
        ChaosAction a = chaos.declencher(run, ChaosType.MESSAGE_TRONQUE, Duration.ofSeconds(5), Map.of(), "a");

        assertThat(troncature.actif()).isTrue();
        assertThat(a.parametres()).containsEntry("proportion", 0.1);
    }

    @Test
    void laDureeEstObligatoireEtPlafonnee() {
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, null, Map.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duree");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ZERO, Map.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(61), Map.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maximale de 60 s");

        verify(toxiproxy, never()).desactiverProxy();   // refuse AVANT tout effet
        verify(store, never()).creer(any());
    }

    @Test
    void lesParametresInvalidesSontRefusesSansEffet() {
        Duration d = Duration.ofSeconds(5);
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.SURCHARGE, d, Map.of("multiplicateur", 7), "a"))
                .hasMessageContaining("multiplicateur");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.SURCHARGE, d, Map.of(), "a"))
                .hasMessageContaining("obligatoire");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.LATENCE, d, Map.of("latenceMs", 5000), "a"))
                .hasMessageContaining("entre 1 et 2000");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.LATENCE, d, Map.of("latenceMs", "vite"), "a"))
                .hasMessageContaining("entier");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, d, Map.of("mode", "BOUM"), "a"))
                .hasMessageContaining("mode");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, d, Map.of("mod", "x"), "a"))
                .hasMessageContaining("inconnu");
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.MESSAGE_TRONQUE, d, Map.of("proportion", 1.5), "a"))
                .hasMessageContaining("proportion");

        verify(store, never()).creer(any());
        assertThat(charge.actif()).isFalse();
    }

    @Test
    void unRunInconnuOuInactifEstRefuse() {
        assertThatThrownBy(() -> chaos.declencher(UUID.randomUUID(), ChaosType.COUPURE_BASE, Duration.ofSeconds(5),
                Map.of(), "a")).hasMessageContaining("Run inconnu");

        etatDuRun = EtatRun.ARRETE;
        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(5), Map.of(), "a"))
                .isInstanceOf(IllegalStateException.class);
        verify(toxiproxy, never()).desactiverProxy();
    }

    @Test
    void uneSeuleActionActiveParType() {
        chaos.declencher(run, ChaosType.LATENCE, Duration.ofSeconds(5), Map.of("latenceMs", 100), "a");

        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.LATENCE, Duration.ofSeconds(5),
                Map.of("latenceMs", 200), "a")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deja en cours");
        // Un autre type reste possible : on peut cumuler latence et surcharge.
        chaos.declencher(run, ChaosType.SURCHARGE, Duration.ofSeconds(5), Map.of("multiplicateur", 5), "a");
    }

    @Test
    void siToxiproxyRefuseAucuneTraceNeReste() {
        doThrow(new ChaosIndisponibleException("Toxiproxy injoignable")).when(toxiproxy).desactiverProxy();

        assertThatThrownBy(() -> chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(5), Map.of(), "a"))
                .isInstanceOf(ChaosIndisponibleException.class);

        verify(store).supprimer(any());
        assertThat(chaos.actif(ChaosType.COUPURE_BASE)).isFalse();
    }

    @Test
    void toutRetablirAnnuleImmediatementEtFermeLesTraces() {
        chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(30), Map.of(), "a");
        chaos.declencher(run, ChaosType.SURCHARGE, Duration.ofSeconds(30), Map.of("multiplicateur", 100), "a");
        chaos.declencher(run, ChaosType.MESSAGE_TRONQUE, Duration.ofSeconds(30), Map.of(), "a");

        List<ChaosAction> finies = chaos.retablirTout(run, "bob");

        assertThat(finies).hasSize(3).allSatisfy(f -> {
            assertThat(f.motifFin()).isEqualTo("RETABLIE");
            assertThat(f.fin()).isNotNull();
        });
        verify(toxiproxy).retablirTout();
        verify(toxiproxy).activerProxy();
        assertThat(charge.facteur()).isEqualTo(1.0);
        assertThat(troncature.actif()).isFalse();
        verify(store, org.mockito.Mockito.times(3)).terminer(any(), any(), eq("RETABLIE"), anyLong());
        assertThat(chaos.actif(ChaosType.COUPURE_BASE)).isFalse();
    }

    @Test
    void toutRetablirNeMentPasSiLaLiaisonEstPeutEtreEncoreCoupee() {
        chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(30), Map.of(), "a");
        doThrow(new ChaosIndisponibleException("Toxiproxy injoignable")).when(toxiproxy).retablirTout();

        assertThatThrownBy(() -> chaos.retablirTout(run, "bob")).isInstanceOf(ChaosIndisponibleException.class);
        assertThat(chaos.actif(ChaosType.COUPURE_BASE)).isTrue();   // toujours suivie, donc retentee a l'echeance
    }

    @Test
    void touRetablirSansActionNEstPasUneErreurMemeSiToxiproxyEstAbsent() {
        doThrow(new ChaosIndisponibleException("Toxiproxy injoignable")).when(toxiproxy).retablirTout();

        assertThat(chaos.retablirTout(run, "bob")).isEmpty();
    }

    @Test
    void uneActionPrendFinToutSeuleAEcheance() {
        chaos.declencher(run, ChaosType.SURCHARGE, Duration.ofSeconds(1), Map.of("multiplicateur", 20), "a");
        assertThat(charge.actif()).isTrue();

        await().atMost(Duration.ofSeconds(4)).until(() -> !charge.actif());

        verify(store).terminer(any(), any(), eq("EXPIREE"), anyLong());
        assertThat(chaos.actif(ChaosType.SURCHARGE)).isFalse();
    }

    @Test
    void uneActionPrendFinQuandLeRunSArrete() {
        chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(60), Map.of(), "a");

        etatDuRun = EtatRun.ARRETE;

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> verify(toxiproxy).activerProxy());
        verify(store).terminer(any(), any(), eq("RUN_TERMINE"), anyLong());
    }

    @Test
    void uneActionQuiNePeutPasEtreRetireeEstRetenteAuTopSuivant() {
        chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(1), Map.of(), "a");
        org.mockito.Mockito.doThrow(new ChaosIndisponibleException("Toxiproxy injoignable")).doNothing()
                .when(toxiproxy).activerProxy();

        await().atMost(Duration.ofSeconds(5)).until(() -> !chaos.actif(ChaosType.COUPURE_BASE));

        verify(toxiproxy, org.mockito.Mockito.atLeast(2)).activerProxy();
    }
}

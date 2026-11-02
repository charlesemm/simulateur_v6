package ci.cnam.simulator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ci.cnam.simulator.core.scenario.Phase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CadenceTest {

    @Test
    void debitConstantSansPhase() {
        Cadence c = new Cadence(10, List.of());

        assertThat(c.cumul(5)).isCloseTo(50, within(1e-9));
        assertThat(c.instant(0)).isEqualTo(0);
        assertThat(c.instant(25)).isCloseTo(2.5, within(1e-9));
    }

    @Test
    void phaseDeSurchargeMultiplieLeDebitPuisRevientAuNormal() {
        // 0-2 s : x1 implicite (debit 10) ; 2-4 s : x20 (200/s) ; ensuite x1.
        Cadence c = new Cadence(10, List.of(new Phase(Duration.ofSeconds(2), Duration.ofSeconds(4), 20, "x20")));

        assertThat(c.cumul(2)).isCloseTo(20, within(1e-9));
        assertThat(c.cumul(4)).isCloseTo(20 + 400, within(1e-9));
        assertThat(c.cumul(5)).isCloseTo(20 + 400 + 10, within(1e-9));
    }

    @Test
    void instantEstLInverseDeCumul() {
        Cadence c = new Cadence(10, List.of(new Phase(Duration.ofSeconds(2), Duration.ofSeconds(4), 20, "x20")));

        for (long k : new long[] {0, 5, 20, 21, 220, 420, 430, 1000}) {
            assertThat(c.cumul(c.instant(k))).isCloseTo(k, within(1e-6));
        }
    }

    @Test
    void phaseCommencantAZeroSansTrou() {
        Cadence c = new Cadence(4, List.of(new Phase(Duration.ZERO, Duration.ofSeconds(10), 0.5, "x0.5")));

        assertThat(c.cumul(10)).isCloseTo(20, within(1e-9));
        assertThat(c.cumul(11)).isCloseTo(24, within(1e-9));
    }
}

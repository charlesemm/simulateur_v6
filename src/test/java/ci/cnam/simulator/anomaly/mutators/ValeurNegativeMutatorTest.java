package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValeurNegativeMutatorTest {

    private final ValeurNegativeMutator mutator = new ValeurNegativeMutator();

    private Object muter(Object valeur) {
        return mutator.muter(valeur, new AleaScripte()).orElseThrow().valeur();
    }

    @Test
    void gardeLeTypeDeLaValeur() {
        assertThat(muter(5000)).isEqualTo(-5000);
        assertThat(muter(5000L)).isEqualTo(-5000L);
        assertThat(muter(12.5)).isEqualTo(-12.5);
        assertThat(muter(new BigDecimal("5000.00"))).isEqualTo(new BigDecimal("-5000.00"));
        assertThat(muter("5000")).isEqualTo("-5000");
    }

    @Test
    void rienAFaireSurZeroUnNegatifOuUnNonNombre() {
        assertThat(mutator.muter(0, new AleaScripte())).isEmpty();
        assertThat(mutator.muter(-3, new AleaScripte())).isEmpty();
        assertThat(mutator.muter("abc", new AleaScripte())).isEmpty();
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
        assertThat(mutator.muter(true, new AleaScripte())).isEmpty();
    }
}

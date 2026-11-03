package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FormatDateInvalideMutatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private final FormatDateInvalideMutator mutator = new FormatDateInvalideMutator();

    private Object muter(Object valeur, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        Object resultat = mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void jourImpossible() {
        assertThat(muter(DATE, 0, 35)).isEqualTo("2026-03-35");
        // fevrier 2026 n'a que 28 jours : le 31 fevrier de l'exemple du cahier des charges
        assertThat(muter(LocalDate.of(2026, 2, 10), 0, 31)).isEqualTo("2026-02-31");
    }

    @Test
    void moisSuperieurADouze() {
        assertThat(muter(DATE, 1, 13)).isEqualTo("2026-13-10");
    }

    @Test
    void texteLibre() {
        assertThat(muter(DATE, 2, 1)).isEqualTo("date inconnue");
    }

    @Test
    void mauvaisFormat() {
        assertThat(muter(DATE, 3, 0)).isEqualTo("10/03/2026");
        assertThat(muter(DATE, 3, 1)).isEqualTo("20260310");
        assertThat(muter(DATE, 3, 2)).isEqualTo("10.03.26");
        assertThat(muter(DATE, 3, 3)).isEqualTo("03-10-2026");
    }

    @Test
    void accepteUneDateEcriteEnTexteIso() {
        assertThat(muter("1990-05-17", 1, 20)).isEqualTo("1990-20-17");
    }

    @Test
    void rienAFaireSiCeNEstPasUneDate() {
        assertThat(mutator.muter("pas une date", new AleaScripte())).isEmpty();
        assertThat(mutator.muter(12, new AleaScripte())).isEmpty();
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
    }
}

package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FormatNumeriqueInvalideMutatorTest {

    private final FormatNumeriqueInvalideMutator mutator = new FormatNumeriqueInvalideMutator();

    private Object muter(Object valeur, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        Object resultat = mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void lettreGlisseeDansLesChiffres() {
        // variante 0 ; lettre 'a' ; inseree a l'indice 2 : 120 -> 12a0 (l'exemple du cahier des charges)
        assertThat(muter(120, 0, 0, 2)).isEqualTo("12a0");
        assertThat(muter("120", 0, 0, 2)).isEqualTo("12a0");
    }

    @Test
    void separateurDouble() {
        assertThat(muter(120, 1, 0, 2)).isEqualTo("12..0");
        assertThat(muter(120, 1, 1, 1)).isEqualTo("1,,20");
    }

    @Test
    void texteALaPlaceDuNombre() {
        assertThat(muter(120, 2, 1)).isEqualTo("n/a");
    }

    @Test
    void unDecimalEstEcritSansNotationScientifique() {
        assertThat(muter(new BigDecimal("12.50"), 0, 1, 0)).isEqualTo("b12.50");
    }

    @Test
    void rienAFaireSurUneValeurNonNumerique() {
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
        assertThat(mutator.muter(true, new AleaScripte())).isEmpty();
        assertThat(mutator.muter("  ", new AleaScripte())).isEmpty();
    }
}

package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChampVideMutatorTest {

    private final ChampVideMutator mutator = new ChampVideMutator();

    private Object muter(Object valeur, int tirage) {
        AleaScripte alea = new AleaScripte(tirage);
        Object resultat = mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void tirage0MetNull() {
        assertThat(muter("Kouassi", 0)).isNull();
    }

    @Test
    void tirage1DonneUneChaineVide() {
        assertThat(muter("Kouassi", 1)).isEqualTo("");
    }

    @Test
    void tirage2DonneDesEspacesSeuls() {
        assertThat(muter("Kouassi", 2)).isEqualTo("   ");
    }

    @Test
    void videAussiUnNombre() {
        assertThat(muter(120, 1)).isEqualTo("");
    }
}

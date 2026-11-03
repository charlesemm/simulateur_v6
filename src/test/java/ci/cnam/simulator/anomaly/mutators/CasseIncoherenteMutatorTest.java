package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CasseIncoherenteMutatorTest {

    private final CasseIncoherenteMutator mutator = new CasseIncoherenteMutator();

    private String muter(String valeur, int tirage) {
        AleaScripte alea = new AleaScripte(tirage);
        String resultat = (String) mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void majuscules() {
        assertThat(muter("Kouassi", 0)).isEqualTo("KOUASSI");
    }

    @Test
    void minuscules() {
        assertThat(muter("Kouassi", 1)).isEqualTo("kouassi");
    }

    @Test
    void alternance() {
        assertThat(muter("Kouassi", 2)).isEqualTo("kOuAsSi");
    }

    @Test
    void siLaVarianteNeChangeRienOnPrendLaSuivante() {
        assertThat(muter("KOUASSI", 0)).isEqualTo("kouassi");
    }

    @Test
    void rienAFaireSiLaCasseNeChangePas() {
        assertThat(mutator.muter("123", new AleaScripte(0))).isEmpty();
        assertThat(mutator.muter(5, new AleaScripte())).isEmpty();
    }
}

package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValeurHorsListeMutatorTest {

    private final ValeurHorsListeMutator mutator = new ValeurHorsListeMutator();

    private Object muter(Object valeur, int tirage) {
        AleaScripte alea = new AleaScripte(tirage);
        Object resultat = mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void remplaceParUneValeurQuiNExisteDansAucuneListe() {
        assertThat(muter("M", 0)).isEqualTo("X");
        assertThat(muter("AMBULATOIRE", 2)).isEqualTo("N/A");
        assertThat(muter("F", 4)).isEqualTo("99");
    }

    @Test
    void neRendJamaisLaValeurDOrigine() {
        assertThat(muter("X", 0)).isEqualTo("INCONNU");
    }

    @Test
    void rienAFaireSurNull() {
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
    }
}

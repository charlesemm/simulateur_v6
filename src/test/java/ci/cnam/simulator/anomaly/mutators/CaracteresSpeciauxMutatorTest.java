package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaracteresSpeciauxMutatorTest {

    private final CaracteresSpeciauxMutator mutator = new CaracteresSpeciauxMutator();

    private String muter(String valeur, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        String resultat = (String) mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void deuxInsertions() {
        // 2 insertions : '@' a l'indice 3 -> "Kou@assi", puis '#' a la fin (indice 8)
        assertThat(muter("Kouassi", 2, 0, 3, 1, 8)).isEqualTo("Kou@assi#");
    }

    @Test
    void uneInsertionAuDebut() {
        assertThat(muter("Kouassi", 1, 5, 0)).isEqualTo("*Kouassi");
    }

    @Test
    void rienAFaireSurUnTexteVideOuUnNombre() {
        assertThat(mutator.muter("", new AleaScripte())).isEmpty();
        assertThat(mutator.muter(7, new AleaScripte())).isEmpty();
    }
}

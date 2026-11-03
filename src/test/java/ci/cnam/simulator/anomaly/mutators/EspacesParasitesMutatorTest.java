package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EspacesParasitesMutatorTest {

    private final EspacesParasitesMutator mutator = new EspacesParasitesMutator();

    private String muter(String valeur, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        String resultat = (String) mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void espacesAvant() {
        assertThat(muter("Kouassi Yao", 0, 2)).isEqualTo("  Kouassi Yao");
    }

    @Test
    void espacesApres() {
        assertThat(muter("Kouassi Yao", 1, 3)).isEqualTo("Kouassi Yao   ");
    }

    @Test
    void espaceExistantDouble() {
        assertThat(muter("Kouassi Yao", 2, 0)).isEqualTo("Kouassi  Yao");
    }

    @Test
    void sansEspaceADoublerOnEnAjouteAvant() {
        assertThat(muter("Kouassi", 2, 1)).isEqualTo(" Kouassi");
    }

    @Test
    void rienAFaireSurUnTexteVideOuUnNombre() {
        assertThat(mutator.muter("", new AleaScripte(0))).isEmpty();
        assertThat(mutator.muter(12, new AleaScripte(0))).isEmpty();
    }
}

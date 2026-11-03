package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatIdentifiantInvalideMutatorTest {

    private final FormatIdentifiantInvalideMutator mutator = new FormatIdentifiantInvalideMutator();

    private String muter(String valeur, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        String resultat = (String) mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void numeroTropCourt() {
        assertThat(muter("1234567890", 0, 4)).isEqualTo("1234");
    }

    @Test
    void lettreAlaPlaceDUnChiffre() {
        // 3e chiffre (indice 2) remplace par 'A' + 7 = 'H'
        assertThat(muter("1234567890", 1, 2, 7)).isEqualTo("12H4567890");
    }

    @Test
    void chiffresEnTrop() {
        // 2 chiffres ajoutes : 4 puis 7
        assertThat(muter("1234567890", 2, 2, 4, 7)).isEqualTo("123456789047");
    }

    @Test
    void sansChiffreOnAjouteDesChiffres() {
        assertThat(muter("AB", 1, 1, 5)).isEqualTo("AB5");
    }

    @Test
    void unSeulCaracterePasseALaVarianteSuivante() {
        assertThat(muter("7", 0, 0, 2)).isEqualTo("C");
    }

    @Test
    void rienAFaireSurUneValeurNonTextuelleOuVide() {
        assertThat(mutator.muter(12, new AleaScripte())).isEmpty();
        assertThat(mutator.muter(" ", new AleaScripte())).isEmpty();
    }
}

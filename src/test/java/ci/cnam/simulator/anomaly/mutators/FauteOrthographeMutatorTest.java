package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FauteOrthographeMutatorTest {

    private final FauteOrthographeMutator mutator = new FauteOrthographeMutator();

    private String muter(String valeur, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        String resultat = (String) mutator.muter(valeur, alea).orElseThrow().valeur();
        alea.verifierEpuise();
        return resultat;
    }

    @Test
    void suppressionDUneLettre() {
        // operation 0, 6e candidat (la lettre d'indice 5)
        assertThat(muter("Kouassi", 0, 5)).isEqualTo("Kouasi");
    }

    @Test
    void doublementDUneLettre() {
        assertThat(muter("Kouassi", 1, 6)).isEqualTo("Kouassii");
    }

    @Test
    void permutationDeDeuxLettresVoisinesDifferentes() {
        // candidats : 0, 1, 2, 3, 5 (pas 4 : "ss" permute en "ss") ; le 2e candidat est l'indice 1 (o/u)
        assertThat(muter("Kouassi", 2, 1)).isEqualTo("Kuoassi");
    }

    @Test
    void lettreVoisineDuClavierAzerty() {
        // 'i' : gauche u, droite o, dessous k -> "uok" ; le 3e (indice 2) est 'k'
        assertThat(muter("Kouassi", 3, 6, 2)).isEqualTo("Kouassk");
    }

    @Test
    void laVoisineGardeLaCasseDeLaLettreRemplacee() {
        // 'K' : gauche j, droite l, dessus i -> "jli" ; le 2e (indice 1) est 'l' -> 'L'
        assertThat(muter("KOUASSI", 3, 0, 1)).isEqualTo("LOUASSI");
    }

    @Test
    void permutationImpossibleSurDeuxLettresIdentiquesPasseALOperationSuivante() {
        // "aa" : permutation sans candidat -> voisine ; 'a' a pour voisines z (droite) et q (dessous)
        assertThat(muter("aa", 2, 0, 1)).isEqualTo("qa");
    }

    @Test
    void lesVoisinesSontLuesSurLeClavier() {
        assertThat(FauteOrthographeMutator.voisines('i')).isEqualTo("uok");
        assertThat(FauteOrthographeMutator.voisines('K')).isEqualTo("jli");
        assertThat(FauteOrthographeMutator.voisines('1')).isEmpty();
    }

    @Test
    void rienAFaireSurUnTexteSansLettreOuUneValeurQuiNEstPasUnTexte() {
        assertThat(mutator.muter("123", new AleaScripte(0))).isEmpty();
        assertThat(mutator.muter(42, new AleaScripte(0))).isEmpty();
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
    }
}

package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.random.SeededRandom;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Alea ecrit a l'avance : chaque appel a {@code entre(min, max)} rend la valeur suivante du scenario de test
 * (et verifie qu'elle est dans les bornes demandees). Permet d'ecrire des sorties exactes sans dependre d'une graine.
 */
final class AleaScripte extends SeededRandom {

    private final Deque<Integer> valeurs = new ArrayDeque<>();

    AleaScripte(int... tirages) {
        super(1);
        for (int t : tirages) {
            valeurs.add(t);
        }
    }

    @Override
    public int entre(int minInclus, int maxInclus) {
        assertThat(valeurs).as("tirage inattendu entre(%d, %d)", minInclus, maxInclus).isNotEmpty();
        int v = valeurs.removeFirst();
        assertThat(v).as("tirage hors bornes [%d, %d]", minInclus, maxInclus).isBetween(minInclus, maxInclus);
        return v;
    }

    /** Tous les tirages prevus ont ete consommes, ni plus ni moins. */
    void verifierEpuise() {
        assertThat(valeurs).as("tirages non consommes").isEmpty();
    }
}

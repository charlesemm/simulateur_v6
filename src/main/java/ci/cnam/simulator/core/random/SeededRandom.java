package ci.cnam.simulator.core.random;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Unique point d'alea du simulateur, pilote par une graine : meme graine, meme suite de tirages.
 * Etend {@link Random} pour pouvoir etre passe tel quel a Datafaker.
 * Interdit ailleurs : Math.random() et new Random().
 */
public class SeededRandom extends Random {

    private final long graine;

    public SeededRandom(long graine) {
        super(graine);
        this.graine = graine;
    }

    public long graine() {
        return graine;
    }

    /** Entier dans [minInclus, maxInclus]. */
    public int entre(int minInclus, int maxInclus) {
        return nextInt(minInclus, maxInclus + 1);
    }

    /** Vrai avec la probabilite donnee (0.0 a 1.0). */
    public boolean chance(double probabilite) {
        return nextDouble() < probabilite;
    }

    public <T> T choisir(List<T> elements) {
        return elements.get(nextInt(elements.size()));
    }

    /** UUID version 4 reproductible. */
    public UUID nextUuid() {
        long haut = (nextLong() & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
        long bas = (nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(haut, bas);
    }
}

package ci.cnam.simulator.chaos;

import ci.cnam.simulator.core.engine.MultiplicateurCharge;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Multiplie le debit du scenario (x5, x20, x100). Le moteur le lit a chaque pas. Il ne rend pas le systeme plus
 * rapide : il demande plus que ce qu'il peut faire, et c'est la file bornee du sink qui absorbe par contre-pression.
 */
@Component
public class LoadMultiplier implements MultiplicateurCharge {

    /** Seuls paliers acceptes : on ne laisse pas passer un facteur fantaisiste. */
    public static final Set<Integer> PALIERS = Set.of(5, 20, 100);

    private volatile int facteur = 1;

    @Override
    public double facteur() {
        return facteur;
    }

    public void fixer(int nouveau) {
        if (!PALIERS.contains(nouveau)) {
            throw new IllegalArgumentException("multiplicateur : " + PALIERS + " attendu (recu " + nouveau + ")");
        }
        facteur = nouveau;
    }

    public void retablir() {
        facteur = 1;
    }

    public boolean actif() {
        return facteur != 1;
    }
}

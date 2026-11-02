package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.scenario.Phase;
import java.util.ArrayList;
import java.util.List;

/**
 * Rythme theorique d'un domaine : debit par seconde reelle active, module par les phases (hors phase : x1).
 * Calcul pur et deterministe : le k-ieme parcours (k = 0, 1, 2...) a un instant d'arrivee qui ne depend que du
 * scenario, jamais de la vitesse de la machine. C'est ce qui rend les runs reproductibles.
 */
public final class Cadence {

    private final double[] debuts;  // debut de chaque segment, en secondes
    private final double[] taux;    // parcours par seconde dans chaque segment ; le dernier segment est sans fin

    public Cadence(double debit, List<Phase> phases) {
        List<Double> d = new ArrayList<>();
        List<Double> t = new ArrayList<>();
        double curseur = 0;
        for (Phase p : phases) {
            double de = p.de().toNanos() / 1e9;
            double a = p.a().toNanos() / 1e9;
            if (de > curseur) {
                d.add(curseur);
                t.add(debit);
            }
            d.add(de);
            t.add(debit * p.multiplicateur());
            curseur = a;
        }
        d.add(curseur);
        t.add(debit);
        debuts = d.stream().mapToDouble(Double::doubleValue).toArray();
        taux = t.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** Nombre theorique (fractionnaire) de parcours arrives apres {@code secondes} de temps actif. */
    public double cumul(double secondes) {
        double total = 0;
        for (int i = 0; i < debuts.length && secondes > debuts[i]; i++) {
            double fin = i + 1 < debuts.length ? Math.min(secondes, debuts[i + 1]) : secondes;
            total += (fin - debuts[i]) * taux[i];
        }
        return total;
    }

    /** Instant (secondes de temps actif) auquel arrive le k-ieme parcours ; le premier arrive a 0. */
    public double instant(long k) {
        double cumule = 0;
        for (int i = 0; i < debuts.length; i++) {
            boolean dernier = i + 1 == debuts.length;
            double capacite = dernier ? Double.POSITIVE_INFINITY : (debuts[i + 1] - debuts[i]) * taux[i];
            if (k <= cumule + capacite) {
                return debuts[i] + (k - cumule) / taux[i];
            }
            cumule += capacite;
        }
        throw new IllegalStateException("inatteignable : le dernier segment est sans fin");
    }
}

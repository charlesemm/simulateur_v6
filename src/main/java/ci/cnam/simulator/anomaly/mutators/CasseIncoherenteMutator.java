package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Casse incoherente : tout en majuscules (0), tout en minuscules (1) ou en alternance (2, "kOuAsSi").
 * Si la variante tiree ne change rien (texte deja en majuscules, par exemple), on passe a la suivante.
 */
@Component
public class CasseIncoherenteMutator implements Mutator {

    @Override
    public AnomalyType type() {
        return AnomalyType.CASSE_INCOHERENTE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (!(valeur instanceof String texte)) {
            return Optional.empty();
        }
        int depart = random.entre(0, 2);
        for (int essai = 0; essai < 3; essai++) {
            String candidat = variante((depart + essai) % 3, texte);
            if (!candidat.equals(texte)) {
                return Optional.of(new Mutation(candidat));
            }
        }
        return Optional.empty();
    }

    private static String variante(int numero, String t) {
        return switch (numero) {
            case 0 -> t.toUpperCase();
            case 1 -> t.toLowerCase();
            default -> {
                StringBuilder sb = new StringBuilder(t.length());
                for (int i = 0; i < t.length(); i++) {
                    char c = t.charAt(i);
                    sb.append(i % 2 == 0 ? Character.toLowerCase(c) : Character.toUpperCase(c));
                }
                yield sb.toString();
            }
        };
    }
}

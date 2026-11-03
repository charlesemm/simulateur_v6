package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rend un nombre negatif (5000 devient -5000), en gardant son type (entier, decimal ou texte).
 * Sans effet sur zero, sur un nombre deja negatif ou sur une valeur qui n'est pas un nombre.
 */
@Component
public class ValeurNegativeMutator implements Mutator {

    @Override
    public AnomalyType type() {
        return AnomalyType.VALEUR_NEGATIVE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        try {
            BigDecimal nombre = switch (valeur) {
                case Integer i -> BigDecimal.valueOf(i);
                case Long l -> BigDecimal.valueOf(l);
                case Double d -> BigDecimal.valueOf(d);
                case BigDecimal b -> b;
                case String s -> new BigDecimal(s.strip());
                case null, default -> null;
            };
            if (nombre == null || nombre.signum() <= 0) {
                return Optional.empty();
            }
            BigDecimal negatif = nombre.negate();
            Object resultat = switch (valeur) {
                case Integer i -> (Object) (-i);
                case Long l -> (Object) (-l);
                case Double d -> (Object) (-d);
                case String s -> negatif.toPlainString();
                default -> negatif;
            };
            return Optional.of(new Mutation(resultat));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Vide le champ, de trois facons au hasard : null, chaine vide, espaces seuls. */
@Component
public class ChampVideMutator implements Mutator {

    @Override
    public AnomalyType type() {
        return AnomalyType.CHAMP_VIDE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        Object vide = switch (random.entre(0, 2)) {
            case 0 -> null;
            case 1 -> "";
            default -> "   ";
        };
        return Optional.of(new Mutation(vide));
    }
}

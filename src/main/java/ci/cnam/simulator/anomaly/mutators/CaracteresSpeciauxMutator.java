package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Insere un ou deux caracteres speciaux dans le texte : Kouassi devient Kou@ssi#. */
@Component
public class CaracteresSpeciauxMutator implements Mutator {

    static final String SPECIAUX = "@#$%&*!?~^";

    @Override
    public AnomalyType type() {
        return AnomalyType.CARACTERES_SPECIAUX;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (!(valeur instanceof String texte) || texte.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder(texte);
        int insertions = random.entre(1, 2);
        for (int n = 0; n < insertions; n++) {
            char special = SPECIAUX.charAt(random.entre(0, SPECIAUX.length() - 1));
            sb.insert(random.entre(0, sb.length()), special);
        }
        return Optional.of(new Mutation(sb.toString()));
    }
}

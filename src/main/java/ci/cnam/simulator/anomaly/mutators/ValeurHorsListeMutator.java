package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Remplace une valeur d'un champ a valeurs fixes (genre, type de bon, motif) par une valeur qui n'existe dans
 * aucune de ces listes : X, INCONNU, N/A, ???, 99. Si la valeur d'origine en fait partie, on prend la suivante.
 */
@Component
public class ValeurHorsListeMutator implements Mutator {

    static final List<String> INVALIDES = List.of("X", "INCONNU", "N/A", "???", "99");

    @Override
    public AnomalyType type() {
        return AnomalyType.VALEUR_HORS_LISTE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (valeur == null) {
            return Optional.empty();
        }
        String actuelle = valeur.toString();
        int depart = random.entre(0, INVALIDES.size() - 1);
        for (int essai = 0; essai < INVALIDES.size(); essai++) {
            String candidat = INVALIDES.get((depart + essai) % INVALIDES.size());
            if (!candidat.equals(actuelle)) {
                return Optional.of(new Mutation(candidat));
            }
        }
        return Optional.empty();
    }
}

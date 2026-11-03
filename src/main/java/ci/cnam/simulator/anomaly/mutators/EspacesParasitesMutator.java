package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Espaces en trop : avant (0), apres (1) ou un espace existant double (2, ou avant si le texte n'a aucun espace).
 * Le nombre d'espaces ajoutes avant ou apres est tire entre 1 et 3.
 */
@Component
public class EspacesParasitesMutator implements Mutator {

    @Override
    public AnomalyType type() {
        return AnomalyType.ESPACES_PARASITES;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (!(valeur instanceof String texte) || texte.isEmpty()) {
            return Optional.empty();
        }
        int variante = random.entre(0, 2);
        if (variante == 2) {
            List<Integer> espaces = new ArrayList<>();
            for (int i = 0; i < texte.length(); i++) {
                if (texte.charAt(i) == ' ') {
                    espaces.add(i);
                }
            }
            if (!espaces.isEmpty()) {
                int i = espaces.get(random.entre(0, espaces.size() - 1));
                return Optional.of(new Mutation(texte.substring(0, i) + " " + texte.substring(i)));
            }
            variante = 0;
        }
        String espaces = " ".repeat(random.entre(1, 3));
        return Optional.of(new Mutation(variante == 0 ? espaces + texte : texte + espaces));
    }
}

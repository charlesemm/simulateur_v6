package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Identifiant mal forme : tronque (0, numero trop court), une lettre a la place d'un chiffre (1)
 * ou des chiffres en trop (2). Si la variante tiree est impossible (un seul caractere, aucun chiffre),
 * on passe a la suivante.
 */
@Component
public class FormatIdentifiantInvalideMutator implements Mutator {

    @Override
    public AnomalyType type() {
        return AnomalyType.FORMAT_IDENTIFIANT_INVALIDE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (!(valeur instanceof String id) || id.isBlank()) {
            return Optional.empty();
        }
        int depart = random.entre(0, 2);
        for (int essai = 0; essai < 3; essai++) {
            switch ((depart + essai) % 3) {
                case 0 -> {
                    if (id.length() >= 2) {
                        return Optional.of(new Mutation(id.substring(0, random.entre(1, id.length() - 1))));
                    }
                }
                case 1 -> {
                    List<Integer> chiffres = new ArrayList<>();
                    for (int i = 0; i < id.length(); i++) {
                        if (Character.isDigit(id.charAt(i))) {
                            chiffres.add(i);
                        }
                    }
                    if (!chiffres.isEmpty()) {
                        int i = chiffres.get(random.entre(0, chiffres.size() - 1));
                        char lettre = (char) ('A' + random.entre(0, 25));
                        return Optional.of(new Mutation(id.substring(0, i) + lettre + id.substring(i + 1)));
                    }
                }
                default -> {
                    StringBuilder sb = new StringBuilder(id);
                    int combien = random.entre(1, 3);
                    for (int n = 0; n < combien; n++) {
                        sb.append((char) ('0' + random.entre(0, 9)));
                    }
                    return Optional.of(new Mutation(sb.toString()));
                }
            }
        }
        return Optional.empty();
    }
}

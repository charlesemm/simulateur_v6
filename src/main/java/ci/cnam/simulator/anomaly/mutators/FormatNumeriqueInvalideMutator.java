package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Nombre illisible, de trois facons : une lettre glissee dans les chiffres (0, 120 devient 12a0),
 * un separateur double (1, 12..0) ou un texte a la place du nombre (2, "n/a"). Accepte un nombre ou son texte.
 */
@Component
public class FormatNumeriqueInvalideMutator implements Mutator {

    static final List<String> TEXTES = List.of("NaN", "n/a", "inconnu", "--", "douze");

    @Override
    public AnomalyType type() {
        return AnomalyType.FORMAT_NUMERIQUE_INVALIDE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        String texte = texte(valeur);
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        String corrompue = switch (random.entre(0, 2)) {
            case 0 -> {
                char lettre = (char) ('a' + random.entre(0, 25));
                int i = random.entre(0, texte.length());
                yield texte.substring(0, i) + lettre + texte.substring(i);
            }
            case 1 -> {
                String separateur = random.entre(0, 1) == 0 ? ".." : ",,";
                int i = random.entre(1, texte.length());
                yield texte.substring(0, i) + separateur + texte.substring(i);
            }
            default -> TEXTES.get(random.entre(0, TEXTES.size() - 1));
        };
        return Optional.of(new Mutation(corrompue));
    }

    private static String texte(Object valeur) {
        if (valeur instanceof BigDecimal b) {
            return b.toPlainString();
        }
        if (valeur instanceof Number || valeur instanceof String) {
            return valeur.toString();
        }
        return null;
    }
}

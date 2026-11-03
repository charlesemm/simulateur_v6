package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Faute de frappe sur une lettre : suppression (0), doublement (1), permutation de deux lettres voisines (2)
 * ou remplacement par une touche voisine du clavier AZERTY (3). Si l'operation tiree est impossible sur ce mot,
 * on essaie la suivante. Kouassi peut devenir Kouasi, Kouassii, Kuoassi ou Kouassu.
 */
@Component
public class FauteOrthographeMutator implements Mutator {

    private static final String[] RANGEES = {"azertyuiop", "qsdfghjklm", "wxcvbn"};

    @Override
    public AnomalyType type() {
        return AnomalyType.FAUTE_ORTHOGRAPHE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (!(valeur instanceof String texte)) {
            return Optional.empty();
        }
        int depart = random.entre(0, 3);
        for (int essai = 0; essai < 4; essai++) {
            int operation = (depart + essai) % 4;
            List<Integer> positions = positions(texte, operation);
            if (!positions.isEmpty()) {
                int i = positions.get(random.entre(0, positions.size() - 1));
                return Optional.of(new Mutation(appliquer(texte, operation, i, random)));
            }
        }
        return Optional.empty();
    }

    private static List<Integer> positions(String t, int operation) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) {
            boolean ok = switch (operation) {
                case 0 -> t.length() >= 2 && Character.isLetter(t.charAt(i));
                case 1 -> Character.isLetter(t.charAt(i));
                case 2 -> i + 1 < t.length() && Character.isLetter(t.charAt(i)) && Character.isLetter(t.charAt(i + 1))
                        && t.charAt(i) != t.charAt(i + 1);
                default -> !voisines(t.charAt(i)).isEmpty();
            };
            if (ok) {
                positions.add(i);
            }
        }
        return positions;
    }

    private static String appliquer(String t, int operation, int i, SeededRandom random) {
        return switch (operation) {
            case 0 -> t.substring(0, i) + t.substring(i + 1);
            case 1 -> t.substring(0, i + 1) + t.substring(i);
            case 2 -> t.substring(0, i) + t.charAt(i + 1) + t.charAt(i) + t.substring(i + 2);
            default -> {
                String voisines = voisines(t.charAt(i));
                char v = voisines.charAt(random.entre(0, voisines.length() - 1));
                yield t.substring(0, i) + (Character.isUpperCase(t.charAt(i)) ? Character.toUpperCase(v) : v)
                        + t.substring(i + 1);
            }
        };
    }

    /** Touches a gauche, a droite, au-dessus et en dessous (dans cet ordre) sur un clavier AZERTY. */
    static String voisines(char lettre) {
        char c = Character.toLowerCase(lettre);
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < RANGEES.length; r++) {
            int col = RANGEES[r].indexOf(c);
            if (col < 0) {
                continue;
            }
            ajouter(sb, r, col - 1);
            ajouter(sb, r, col + 1);
            ajouter(sb, r - 1, col);
            ajouter(sb, r + 1, col);
        }
        return sb.toString();
    }

    private static void ajouter(StringBuilder sb, int rangee, int col) {
        if (rangee >= 0 && rangee < RANGEES.length && col >= 0 && col < RANGEES[rangee].length()) {
            sb.append(RANGEES[rangee].charAt(col));
        }
    }
}

package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Le meme patient saisi deux fois, ecrit un peu differemment : « N'Guessan » puis « Nguessan ». L'originale
 * n'est PAS touchee ; c'est une SECONDE ligne qui part, avec son propre identifiant, et qui reference
 * l'originale par {@code doublon_de} comme le doublon exact.
 *
 * <p>Cinq variations, dans l'ordre de tirage, la suivante essayee si celle-ci est impossible sur ce nom :
 * apostrophe retiree, apostrophe en espace, apostrophe en tiret, espace du nom en tiret, prenoms permutes.
 * La derniere, une variation de finale (Kouassi / Kouassy, Konan / Konann), marche toujours : sans elle, le
 * doublon approximatif retomberait en doublon exact, ce qui n'est pas la meme anomalie.
 */
@Component
public class DoublonApproximatifMutator implements MutatorEvenement {

    private static final int VARIATIONS = 6;

    @Override
    public AnomalyType type() {
        return AnomalyType.DOUBLON_APPROXIMATIF;
    }

    @Override
    public Optional<SimEvent> muter(SimEvent original, SeededRandom random) {
        Map<String, Object> payload = new LinkedHashMap<>(original.payload());
        int depart = random.entre(0, VARIATIONS - 1);
        for (int essai = 0; essai < VARIATIONS; essai++) {
            int variation = (depart + essai) % VARIATIONS;
            String champ = variation == 4 ? "prenoms" : "nom";
            Object valeur = payload.get(champ);
            if (!(valeur instanceof String texte) || texte.isBlank()) {
                continue;
            }
            Optional<String> variante = appliquer(variation, texte);
            if (variante.isEmpty() || variante.get().equals(texte)) {
                continue;
            }
            payload.put(champ, variante.get());
            return Optional.of(new SimEvent(random.nextUuid(), original.runId(), original.domaine(), original.type(),
                    original.timestampSimule(), payload,
                    List.of(new AnomalieTracee(type().name(), champ, texte, variante.get(), original.eventId()))));
        }
        // Aucun nom exploitable (nom et prenoms absents ou vides) : pas de doublon approximatif possible.
        return Optional.empty();
    }

    private static Optional<String> appliquer(int variation, String texte) {
        return switch (variation) {
            case 0 -> texte.contains("'") ? Optional.of(texte.replace("'", "")) : Optional.empty();
            case 1 -> texte.contains("'") ? Optional.of(texte.replace("'", " ")) : Optional.empty();
            case 2 -> texte.contains("'") ? Optional.of(texte.replace("'", "-")) : Optional.empty();
            case 3 -> texte.contains(" ") ? Optional.of(texte.replace(" ", "-")) : Optional.empty();
            case 4 -> permuter(texte);
            default -> Optional.of(finale(texte));
        };
    }

    /** « Jean Baptiste » devient « Baptiste Jean » : le meme patient, les prenoms dans l'autre ordre. */
    private static Optional<String> permuter(String texte) {
        String[] mots = texte.split(" ");
        if (mots.length < 2) {
            return Optional.empty();
        }
        String[] permutes = mots.clone();
        permutes[0] = mots[mots.length - 1];
        permutes[mots.length - 1] = mots[0];
        return Optional.of(String.join(" ", permutes));
    }

    /** Variation de finale : Kouassi / Kouassy, Yao / Yaoo, Konan / Konann. */
    private static String finale(String texte) {
        char derniere = texte.charAt(texte.length() - 1);
        String debut = texte.substring(0, texte.length() - 1);
        return switch (Character.toLowerCase(derniere)) {
            case 'i' -> debut + (Character.isUpperCase(derniere) ? 'Y' : 'y');
            case 'y' -> debut + (Character.isUpperCase(derniere) ? 'I' : 'i');
            default -> texte + derniere;
        };
    }
}

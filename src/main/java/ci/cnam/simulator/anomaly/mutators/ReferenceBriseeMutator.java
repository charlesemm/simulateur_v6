package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Un identifiant qui n'existe dans AUCUN referentiel : professionnel de sante, centre de sante, type de centre.
 * L'identifiant invente est BIEN FORME (meme forme, meme longueur, meme prefixe) : c'est le lien qui est rompu,
 * pas le format. Un controle de format ne verra rien ; seule une jointure sur le referentiel trouvera.
 *
 * <p>C'est la seule anomalie qui vise une CLE : les cles ne sont eligibles pour aucun autre type. Le
 * referentiel n'est jamais touche, c'est l'evenement qui pointe dans le vide.
 */
@Component
public class ReferenceBriseeMutator implements Mutator {

    /** Code de referentiel du genre CS-0042 ou PH-0007 : on garde le prefixe et la largeur. */
    private static final Pattern CODE = Pattern.compile("([A-Z]{2,4})-([0-9]{2,8})");

    @Override
    public AnomalyType type() {
        return AnomalyType.REFERENCE_BRISEE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        if (valeur instanceof UUID identifiant) {
            return Optional.of(new Mutation(inexistant(identifiant, random)));
        }
        if (!(valeur instanceof String texte) || texte.isBlank()) {
            return Optional.empty();
        }
        Matcher code = CODE.matcher(texte);
        if (code.matches()) {
            // Meme prefixe, meme nombre de chiffres, numero hors de ce que le referentiel a jamais attribue.
            String chiffres = code.group(2);
            String invente = "9".repeat(chiffres.length() - 1) + random.entre(0, 9);
            return invente.equals(chiffres) ? Optional.empty()
                    : Optional.of(new Mutation(code.group(1) + "-" + invente));
        }
        try {
            return Optional.of(new Mutation(inexistant(UUID.fromString(texte), random).toString()));
        } catch (IllegalArgumentException pasUnUuid) {
            return Optional.empty();
        }
    }

    /** Un UUID version 4 tire de la graine : bien forme, et jamais celui d'une ligne du referentiel. */
    private static UUID inexistant(UUID origine, SeededRandom random) {
        UUID invente = random.nextUuid();
        return invente.equals(origine) ? random.nextUuid() : invente;
    }
}

package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * L'encodage casse d'un nom dans le fichier : « N'Guessan » ecrit {@code N&#39;Guessan} (entite HTML non
 * decodee), {@code N?Guessan} (caractere perdu a la conversion) ou {@code Nâ€™Guessan} (UTF-8 relu en latin-1).
 * Ce sont les trois accidents reels d'un echange de fichiers.
 *
 * <p>Vise le nom a l'interieur de la cellule {@code payload} de la table des evenements, la seule cellule de
 * l'export ou l'identite du patient circule. LA BASE N'EST PAS TOUCHEE : le nom y reste « N'Guessan », et
 * c'est cette valeur saine que le corrige inscrit en valeur d'origine.
 *
 * <p>Un nom sans apostrophe n'est pas casse : il n'y a rien a y perdre, et l'anomalie est alors laissee de cote.
 */
@Component
public class CaracteresCassesMutator implements MutatorFichier {

    /** Le nom tel que le payload json le porte : {@code "nom":"N'Guessan"}. */
    private static final Pattern NOM = Pattern.compile("\"nom\"\\s*:\\s*\"([^\"]*)\"");

    private static final String[] CASSURES = {"&#39;", "?", "â€™"};

    @Override
    public AnomalyType type() {
        return AnomalyType.CARACTERES_CASSES;
    }

    @Override
    public String colonne() {
        return "payload";
    }

    @Override
    public String champ() {
        return "payload.nom";
    }

    @Override
    public Optional<MutationFichier> muter(String cellule, SeededRandom random) {
        if (cellule == null) {
            return Optional.empty();
        }
        Matcher nom = NOM.matcher(cellule);
        if (!nom.find()) {
            return Optional.empty();
        }
        String sain = nom.group(1);
        if (!sain.contains("'")) {
            return Optional.empty();
        }
        String casse = sain.replace("'", CASSURES[random.entre(0, CASSURES.length - 1)]);
        String celluleCassee = cellule.substring(0, nom.start(1)) + casse + cellule.substring(nom.end(1));
        return Optional.of(new MutationFichier(celluleCassee, sain, casse));
    }
}

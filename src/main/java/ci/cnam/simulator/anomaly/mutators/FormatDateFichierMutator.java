package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Une date ecrite dans un format incoherent avec les autres lignes DU MEME FICHIER : toutes les lignes sont
 * en ISO, celle-ci en jour/mois/annee, en format americain (donc ambigu), en compact, ou avec des tirets a
 * l'envers. La valeur reste une date lisible par un humain ; c'est le fichier qui n'est plus homogene.
 *
 * <p>Vise la date de soin (temps simule) de la table des evenements. LA BASE N'EST PAS TOUCHEE : la colonne
 * reste un horodatage, et le corrige garde la forme ISO en valeur d'origine.
 */
@Component
public class FormatDateFichierMutator implements MutatorFichier {

    /** La date telle que l'export l'ecrit : 2026-03-10T08:30:00.000000 */
    private static final Pattern ISO = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?");

    private static final int FORMES = 4;

    @Override
    public AnomalyType type() {
        return AnomalyType.FORMAT_DATE_FICHIER;
    }

    @Override
    public String colonne() {
        return "timestamp_simule";
    }

    @Override
    public String champ() {
        return "timestamp_simule";
    }

    @Override
    public Optional<MutationFichier> muter(String cellule, SeededRandom random) {
        if (cellule == null) {
            return Optional.empty();
        }
        Matcher iso = ISO.matcher(cellule);
        if (!iso.matches()) {
            return Optional.empty();
        }
        String annee = iso.group(1);
        String mois = iso.group(2);
        String jour = iso.group(3);
        String heure = iso.group(4);
        String minute = iso.group(5);
        String seconde = iso.group(6);
        String autre = switch (random.entre(0, FORMES - 1)) {
            case 0 -> jour + "/" + mois + "/" + annee + " " + heure + ":" + minute;
            case 1 -> mois + "/" + jour + "/" + annee + " " + heure + ":" + minute;
            case 2 -> annee + mois + jour + heure + minute + seconde;
            default -> jour + "-" + mois + "-" + annee + " " + heure + ":" + minute + ":" + seconde;
        };
        return autre.equals(cellule) ? Optional.empty() : Optional.of(new MutationFichier(autre, cellule, autre));
    }
}

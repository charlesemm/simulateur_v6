package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;

/**
 * Anomalie qui n'existe QUE DANS LE FICHIER servi : un encodage casse, une date ecrite autrement que les
 * lignes voisines. Une colonne typee ne peut pas les porter, donc elles ne sont pas posees a la generation
 * mais A L'ECRITURE DE L'EXPORT.
 *
 * <p>LA BASE RESTE PROPRE : la valeur en base est la valeur saine, et le corrige garde cette valeur saine en
 * valeur d'origine. Seuls les octets telecharges sont pieges.
 */
public interface MutatorFichier {

    AnomalyType type();

    /** Colonne de la table exportee dont la cellule est reecrite (ex. {@code timestamp_simule}). */
    String colonne();

    /** Nom inscrit au corrige : la cellule, ou le champ visE a l'interieur d'elle (ex. {@code payload.nom}). */
    String champ();

    /**
     * Reecrit la cellule.
     *
     * @param cellule valeur saine, telle que la base la rend (toujours du texte dans un export)
     * @return la cellule piegee et les deux valeurs a inscrire au corrige, ou vide si elle ne s'y prete pas
     */
    Optional<MutationFichier> muter(String cellule, SeededRandom random);
}

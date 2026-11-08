package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Corrompt un champ EN LE JUGEANT CONTRE LES AUTRES champs de l'evenement : une date de soin contre la periode
 * de droits, des droits actifs contre une periode close. Chaque valeur reste valide prise seule ; c'est la
 * relation qui est fausse. Le mutator a donc besoin du payload entier, pas d'une valeur isolee.
 *
 * <p>Il ne change qu'UN champ et le nomme lui-meme : la contrainte uq_anomaly_injected
 * (event_id, type, champ) interdit deux anomalies du meme type sur le meme champ du meme evenement, donc une
 * incoherence s'inscrit au corrige comme UNE ligne, sur le champ qui a bouge.
 */
public interface MutatorRelation {

    AnomalyType type();

    /**
     * Champs du payload qui doivent tous etre presents et non nuls pour que l'anomalie ait un sens
     * (le champ vise ET ceux contre lesquels il se juge).
     */
    List<String> champsRequis();

    /** Rend le champ a changer et sa nouvelle valeur, ou vide si le payload ne s'y prete pas. */
    Optional<MutationRelation> muter(Map<String, Object> payload, SeededRandom random);
}

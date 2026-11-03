package ci.cnam.simulator.anomaly;

import java.util.List;

/**
 * Ce qu'un domaine accepte de laisser corrompre : pour chaque type d'anomalie, les champs de ses evenements
 * qui sont eligibles. Le mecanisme (anomaly/) ne connait aucun champ ; chaque domaine fournit sa declaration.
 */
public interface ChampsEligibles {

    /** Domaine concerne, ex. "prestations". */
    String domaine();

    /** Champs eligibles au type (liste vide si le type ne vise aucun champ, comme DOUBLON_EXACT). */
    List<ChampEligible> champs(AnomalyType type);

    /**
     * @param nom   cle du champ dans le payload de l'evenement
     * @param poids chance relative d'etre choisi parmi les champs eligibles presents : plus fort pour un champ
     *              saisi a la main par l'agent, plus expose dans la realite
     */
    record ChampEligible(String nom, int poids) {

        public ChampEligible {
            if (poids < 1) {
                throw new IllegalArgumentException("poids >= 1 attendu pour " + nom);
            }
        }
    }
}

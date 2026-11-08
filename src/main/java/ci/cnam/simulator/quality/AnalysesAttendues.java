package ci.cnam.simulator.quality;

import java.util.List;

/**
 * Source de verite des analyses attendues. Elle arrive au prompt 15 (planification) : tant qu'aucune
 * implementation n'est presente dans le contexte, {@link AnalyseService} rend une liste vide, et
 * l'endpoint existe deja pour que le script soit ecrit une seule fois.
 */
public interface AnalysesAttendues {

    /** Les echeances ouvertes et non encore honorees, de la plus ancienne a la plus recente. */
    List<AnalyseAttendue> enAttente();
}

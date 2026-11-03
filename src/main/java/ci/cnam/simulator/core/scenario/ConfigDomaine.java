package ci.cnam.simulator.core.scenario;

import java.util.Map;

/**
 * Reglages d'un domaine dans un scenario. Generique : le moteur ne sait pas ce que signifient les cles de parcours.
 *
 * @param debit    parcours (flux unitaires) demarres par seconde REELLE, charge normale
 * @param parcours probabilites du parcours en pourcentage (0 a 100), par nom ; vide = valeurs par defaut du domaine
 * @param anomalies taux d'anomalies en pourcentage (0 a 100), par nom ; vide = aucune anomalie.
 *                  Le moteur ne les interprete pas : le domaine les confie a l'injecteur.
 */
public record ConfigDomaine(double debit, Map<String, Double> parcours, Map<String, Double> anomalies) {

    public ConfigDomaine {
        parcours = Map.copyOf(parcours);
        anomalies = Map.copyOf(anomalies);
    }

    public ConfigDomaine(double debit, Map<String, Double> parcours) {
        this(debit, parcours, Map.of());
    }
}

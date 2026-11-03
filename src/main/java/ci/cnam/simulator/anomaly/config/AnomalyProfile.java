package ci.cnam.simulator.anomaly.config;

import ci.cnam.simulator.anomaly.AnomalyType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Taux d'anomalies d'un domaine dans un scenario : probabilite, par evenement, d'injecter chaque type.
 * Le taux global est leur somme : c'est la proportion d'evenements corrompus (au plus une anomalie par evenement).
 * Taux global 0 = flux de reference propre.
 *
 * <p>Dans le scenario, un taux par type ({@code champVide: 3%}) ou la cle {@code global: 3%}, qui repartit le taux
 * a parts egales entre tous les types (les deux ne se melangent pas).
 *
 * @param taux probabilite (0 a 1) par type ; un type absent vaut 0
 */
public record AnomalyProfile(Map<AnomalyType, Double> taux) {

    public static final String CLE_GLOBAL = "global";
    public static final AnomalyProfile AUCUNE = new AnomalyProfile(Map.of());

    public AnomalyProfile {
        EnumMap<AnomalyType, Double> copie = new EnumMap<>(AnomalyType.class);
        taux.forEach((type, valeur) -> {
            if (valeur == null || valeur.isNaN() || valeur < 0 || valeur > 1) {
                throw new IllegalArgumentException("taux de " + type + " hors de 0 a 1 : " + valeur);
            }
            if (valeur > 0) {
                copie.put(type, valeur);
            }
        });
        if (copie.values().stream().mapToDouble(Double::doubleValue).sum() > 1.0 + 1e-9) {
            throw new IllegalArgumentException("la somme des taux d'anomalies depasse 100 %");
        }
        taux = java.util.Collections.unmodifiableMap(copie);
    }

    public double taux(AnomalyType type) {
        return taux.getOrDefault(type, 0.0);
    }

    /** Proportion d'evenements corrompus. */
    public double tauxGlobal() {
        return taux.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public boolean actif() {
        return !taux.isEmpty();
    }

    /**
     * Construit le profil depuis la section {@code anomalies} d'un scenario (pourcentages de 0 a 100).
     *
     * @throws IllegalArgumentException avec un message clair : type inconnu, melange global/types, somme > 100 %
     */
    public static AnomalyProfile depuis(Map<String, Double> pourcentages) {
        if (pourcentages == null || pourcentages.isEmpty()) {
            return AUCUNE;
        }
        Map<AnomalyType, Double> resultat = new LinkedHashMap<>();
        Double global = pourcentages.get(CLE_GLOBAL);
        if (global != null) {
            if (pourcentages.size() > 1) {
                throw new IllegalArgumentException("domaines.<domaine>.anomalies : '" + CLE_GLOBAL
                        + "' ne se combine pas avec des taux par type");
            }
            for (AnomalyType type : AnomalyType.values()) {
                resultat.put(type, global / 100.0 / AnomalyType.values().length);
            }
            return new AnomalyProfile(resultat);
        }
        for (Map.Entry<String, Double> e : pourcentages.entrySet()) {
            AnomalyType type = AnomalyType.depuis(e.getKey()).orElseThrow(() -> new IllegalArgumentException(
                    "domaines.<domaine>.anomalies." + e.getKey() + " : type inconnu (types permis : "
                            + Arrays.stream(AnomalyType.values()).map(AnomalyType::cle).toList() + " ou '"
                            + CLE_GLOBAL + "')"));
            resultat.put(type, e.getValue() / 100.0);
        }
        double somme = pourcentages.values().stream().mapToDouble(Double::doubleValue).sum();
        if (somme > 100.0 + 1e-9) {
            throw new IllegalArgumentException("domaines.<domaine>.anomalies : la somme des taux depasse 100 % ("
                    + somme + " %)");
        }
        return new AnomalyProfile(resultat);
    }
}

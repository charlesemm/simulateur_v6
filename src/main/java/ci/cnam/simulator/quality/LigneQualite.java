package ci.cnam.simulator.quality;

/**
 * Une ligne du rapport. ratees = injectees - detectees ; fauxPositifs = detections sans anomalie injectee.
 * Les taux sont nuls quand leur denominateur est nul (rien d'injecte, rien de detecte).
 */
public record LigneQualite(String libelle, long injectees, long detectees, long ratees, long fauxPositifs,
                           Double tauxDetection, Double precision) {

    public static LigneQualite de(String libelle, long injectees, long detectees, long fauxPositifs) {
        return new LigneQualite(libelle, injectees, detectees, injectees - detectees, fauxPositifs,
                taux(detectees, injectees), taux(detectees, detectees + fauxPositifs));
    }

    private static Double taux(long numerateur, long denominateur) {
        if (denominateur == 0) {
            return null;
        }
        return Math.round(10_000.0 * numerateur / denominateur) / 10_000.0;
    }
}

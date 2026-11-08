package ci.cnam.simulator.anomaly.mutators;

/**
 * Resultat d'une anomalie de relation : le champ qui a bouge et sa nouvelle valeur. Un seul champ, pour que
 * le corrige garde une valeur d'origine lisible et que uq_anomaly_injected soit respectee.
 *
 * @param champ  cle du payload modifiee
 * @param valeur nouvelle valeur (jamais null : une incoherence n'est pas un champ vide)
 */
public record MutationRelation(String champ, Object valeur) {

    public MutationRelation {
        if (champ == null || champ.isBlank()) {
            throw new IllegalArgumentException("champ attendu");
        }
        if (valeur == null) {
            throw new IllegalArgumentException("une anomalie de relation ne vide pas le champ : " + champ);
        }
    }
}

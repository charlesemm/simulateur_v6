package ci.cnam.simulator.anomaly.mutators;

/**
 * Resultat d'une corruption. La valeur peut etre null (champ mis a null) : c'est pourquoi on l'enveloppe,
 * un {@code Optional} ne pouvant pas porter null.
 */
public record Mutation(Object valeur) {
}

package ci.cnam.simulator.domain.prestations.parcours;

/** Identifiant de chaque etape du parcours (les noeuds du {@link ParcoursGraph}). */
public enum ParcoursEtape {
    VERIFICATION_DROITS,
    OUVERTURE_FACTURE,
    CONSULTATION,
    EXAMENS,
    HOSPITALISATION,
    PRESCRIPTION,
    PHARMACIE,
    FINALISATION
}

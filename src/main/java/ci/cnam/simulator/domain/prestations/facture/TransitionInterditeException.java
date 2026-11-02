package ci.cnam.simulator.domain.prestations.facture;

/** Une transition de statut de facture non autorisee par la machine a etats. */
public class TransitionInterditeException extends RuntimeException {

    private final FactureStatut ancien;
    private final FactureStatut nouveau;

    public TransitionInterditeException(FactureStatut ancien, FactureStatut nouveau) {
        super("Transition interdite : " + ancien + " -> " + nouveau);
        this.ancien = ancien;
        this.nouveau = nouveau;
    }

    public FactureStatut ancien() {
        return ancien;
    }

    public FactureStatut nouveau() {
        return nouveau;
    }
}

package ci.cnam.simulator.core.engine;

/** Facteur applique au debit du scenario (1 = normal). Lu a chaque pas du moteur : il peut changer en cours de run. */
public interface MultiplicateurCharge {

    MultiplicateurCharge AUCUN = () -> 1.0;

    double facteur();
}

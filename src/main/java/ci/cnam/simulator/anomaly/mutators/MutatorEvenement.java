package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;

/** Anomalie qui porte sur l'evenement entier et non sur un champ (le doublon). */
public interface MutatorEvenement {

    AnomalyType type();

    /** Rend l'evenement supplementaire a emettre apres l'original. */
    SimEvent muter(SimEvent original, SeededRandom random);
}

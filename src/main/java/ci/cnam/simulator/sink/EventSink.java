package ci.cnam.simulator.sink;

import ci.cnam.simulator.core.event.SimEvent;

/** Destination des evenements produits (memoire, base par lots, CSV...). */
public interface EventSink {

    void emettre(SimEvent evenement);
}

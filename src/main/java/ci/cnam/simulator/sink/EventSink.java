package ci.cnam.simulator.sink;

import ci.cnam.simulator.core.event.SimEvent;

/** Destination des evenements produits (memoire, base par lots, CSV...). */
public interface EventSink {

    /** Peut bloquer (contre-pression) si la destination ne suit pas ; ne perd jamais un evenement en silence. */
    void emettre(SimEvent evenement);

    /** Rend la main quand tout ce qui a ete emis est durablement ecrit. Sans effet pour une destination synchrone. */
    default void flush() {
    }
}

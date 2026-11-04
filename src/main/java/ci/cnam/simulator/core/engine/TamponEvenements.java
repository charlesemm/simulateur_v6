package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.sink.EventSink;
import java.util.ArrayList;
import java.util.List;

/**
 * Garde les evenements d'UN parcours jusqu'a la validation de ses ecritures. Un parcours annule n'emet rien :
 * on ne publie jamais l'evenement d'une facture qui n'existe pas.
 */
final class TamponEvenements implements EventSink {

    private final List<SimEvent> evenements = new ArrayList<>();

    @Override
    public void emettre(SimEvent evenement) {
        evenements.add(evenement);
    }

    void viderVers(EventSink destination) {
        for (SimEvent e : evenements) {
            destination.emettre(e);
        }
        evenements.clear();
    }
}

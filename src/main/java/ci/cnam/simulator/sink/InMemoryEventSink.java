package ci.cnam.simulator.sink;

import ci.cnam.simulator.core.event.SimEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Garde les evenements en memoire, dans l'ordre d'emission. Prevu pour les tests. */
public class InMemoryEventSink implements EventSink {

    private final List<SimEvent> evenements = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void emettre(SimEvent evenement) {
        evenements.add(evenement);
    }

    public List<SimEvent> evenements() {
        synchronized (evenements) {
            return List.copyOf(evenements);
        }
    }
}

package ci.cnam.simulator.chaos;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.sink.EventSink;

/** Se place devant la destination reelle : applique la troncature quand elle est active, sinon ne change rien. */
public class ChaosEventSink implements EventSink {

    private final EventSink destination;
    private final TroncatureMessages troncature;

    public ChaosEventSink(EventSink destination, TroncatureMessages troncature) {
        this.destination = destination;
        this.troncature = troncature;
    }

    @Override
    public void emettre(SimEvent evenement) {
        destination.emettre(troncature.appliquer(evenement));
    }

    @Override
    public void flush() {
        destination.flush();
    }
}

package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.sink.EcritureAnnexe;
import ci.cnam.simulator.sink.EventSink;
import java.util.ArrayList;
import java.util.List;

/**
 * Garde les evenements (et ecritures annexes) d'UN parcours jusqu'a sa validation. Un parcours annule n'emet rien :
 * on ne publie jamais l'evenement d'une facture qui n'existe pas. A la vidange, les ecritures annexes partent AVANT
 * les evenements : un evenement n'arrive jamais en base avant la facture dont il parle.
 */
final class TamponEvenements implements EventSink {

    private final List<SimEvent> evenements = new ArrayList<>();
    private final List<EcritureAnnexe> annexes = new ArrayList<>();

    @Override
    public void emettre(SimEvent evenement) {
        evenements.add(evenement);
    }

    @Override
    public void joindre(EcritureAnnexe annexe) {
        annexes.add(annexe);
    }

    void viderVers(EventSink destination) {
        for (EcritureAnnexe a : annexes) {
            destination.joindre(a);
        }
        for (SimEvent e : evenements) {
            destination.emettre(e);
        }
        annexes.clear();
        evenements.clear();
    }
}

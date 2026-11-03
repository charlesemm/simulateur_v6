package ci.cnam.simulator.anomaly;

import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.sink.EventSink;

/**
 * Se place entre un generateur et la destination des evenements : chaque evenement propre passe par
 * l'{@link AnomalyInjector} avant d'etre emis. Le generateur ne sait pas qu'il est corrompu.
 */
public final class InjectionSink implements EventSink {

    private final EventSink destination;
    private final AnomalyInjector injecteur;
    private final AnomalyProfile profil;

    public InjectionSink(EventSink destination, AnomalyInjector injecteur, AnomalyProfile profil) {
        this.destination = destination;
        this.injecteur = injecteur;
        this.profil = profil;
    }

    @Override
    public void emettre(SimEvent evenement) {
        injecteur.injecter(evenement, profil).forEach(destination::emettre);
    }

    @Override
    public void flush() {
        destination.flush();
    }
}

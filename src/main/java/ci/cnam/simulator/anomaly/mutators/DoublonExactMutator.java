package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Le meme evenement envoye deux fois. Le doublon reprend tout de l'original sauf son identifiant (nouveau,
 * pour que la contrainte d'unicite de event_id reste intacte) et reference l'original.
 */
@Component
public class DoublonExactMutator implements MutatorEvenement {

    @Override
    public AnomalyType type() {
        return AnomalyType.DOUBLON_EXACT;
    }

    @Override
    public Optional<SimEvent> muter(SimEvent original, SeededRandom random) {
        return Optional.of(new SimEvent(random.nextUuid(), original.runId(), original.domaine(), original.type(),
                original.timestampSimule(), new LinkedHashMap<>(original.payload()),
                List.of(new AnomalieTracee(type().name(), null, null, null, original.eventId()))));
    }
}

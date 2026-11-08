package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoublonExactMutatorTest {

    private final DoublonExactMutator mutator = new DoublonExactMutator();

    @Test
    void leDoublonReprendToutSaufSonIdentifiantEtReferenceLOriginal() {
        UUID run = new UUID(1, 1);
        SimEvent original = new SimEvent(new UUID(5, 5), run, "prestations", "FACTURE_OUVERTE",
                LocalDateTime.of(2026, 3, 10, 8, 30), Map.of("nom", "Kouassi", "quantite", 2), List.of());

        SimEvent doublon = mutator.muter(original, new SeededRandom(7)).orElseThrow();

        assertThat(doublon.eventId()).isEqualTo(new SeededRandom(7).nextUuid()).isNotEqualTo(original.eventId());
        assertThat(doublon.runId()).isEqualTo(run);
        assertThat(doublon.domaine()).isEqualTo("prestations");
        assertThat(doublon.type()).isEqualTo("FACTURE_OUVERTE");
        assertThat(doublon.timestampSimule()).isEqualTo(LocalDateTime.of(2026, 3, 10, 8, 30));
        assertThat(doublon.payload()).isEqualTo(original.payload());
        assertThat(doublon.anomalies())
                .containsExactly(new AnomalieTracee("DOUBLON_EXACT", null, null, null, original.eventId()));
        assertThat(original.anomalies()).isEmpty();
    }
}

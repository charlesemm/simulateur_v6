package ci.cnam.simulator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.sink.EcritureAnnexe;
import ci.cnam.simulator.sink.EventSink;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TamponEvenementsTest {

    private record Annexe(String nom) implements EcritureAnnexe {
    }

    @Test
    void lesEcrituresAnnexesPartentAvantLesEvenementsDuMemeParcours() {
        TamponEvenements tampon = new TamponEvenements();
        // Le parcours emet ses evenements puis, a la fin, joint sa facture.
        tampon.emettre(evenement("PATIENT_ARRIVE"));
        tampon.emettre(evenement("FACTURE_OUVERTE"));
        tampon.joindre(new Annexe("facture"));

        List<String> ordre = new ArrayList<>();
        tampon.viderVers(new EventSink() {
            @Override
            public void emettre(SimEvent e) {
                ordre.add(e.type());
            }

            @Override
            public void joindre(EcritureAnnexe a) {
                ordre.add(((Annexe) a).nom());
            }
        });

        assertThat(ordre).containsExactly("facture", "PATIENT_ARRIVE", "FACTURE_OUVERTE");
    }

    @Test
    void unTamponVideNEmetPlusRien() {
        TamponEvenements tampon = new TamponEvenements();
        tampon.emettre(evenement("A"));
        tampon.joindre(new Annexe("x"));
        tampon.viderVers(new EventSink() {
            @Override
            public void emettre(SimEvent e) {
            }

            @Override
            public void joindre(EcritureAnnexe a) {
            }
        });

        List<String> reste = new ArrayList<>();
        tampon.viderVers(new EventSink() {
            @Override
            public void emettre(SimEvent e) {
                reste.add(e.type());
            }

            @Override
            public void joindre(EcritureAnnexe a) {
                reste.add("annexe");
            }
        });
        assertThat(reste).isEmpty();
    }

    private static SimEvent evenement(String type) {
        return new SimEvent(UUID.randomUUID(), new UUID(0, 1), "prestations", type,
                LocalDateTime.of(2026, 3, 1, 8, 0), Map.of(), null);
    }
}

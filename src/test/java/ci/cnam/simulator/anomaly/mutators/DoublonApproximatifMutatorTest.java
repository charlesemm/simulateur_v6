package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoublonApproximatifMutatorTest {

    private static final UUID ORIGINAL = new UUID(5, 5);

    private final DoublonApproximatifMutator mutator = new DoublonApproximatifMutator();

    private SimEvent original(String nom, String prenoms) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nom", nom);
        payload.put("prenoms", prenoms);
        payload.put("quantite", 2);
        return new SimEvent(ORIGINAL, new UUID(1, 1), "prestations", "FACTURE_OUVERTE",
                LocalDateTime.of(2026, 3, 10, 8, 30), payload, List.of());
    }

    private SimEvent doublon(SimEvent original, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        SimEvent doublon = mutator.muter(original, alea).orElseThrow();
        alea.verifierEpuise();
        return doublon;
    }

    @Test
    void apostropheRetiree() {
        SimEvent original = original("N'Guessan", "Awa");

        SimEvent doublon = doublon(original, 0);

        assertThat(doublon.payload().get("nom")).isEqualTo("NGuessan");
        assertThat(doublon.payload().get("prenoms")).isEqualTo("Awa");
        assertThat(doublon.anomalies()).containsExactly(
                new AnomalieTracee("DOUBLON_APPROXIMATIF", "nom", "N'Guessan", "NGuessan", ORIGINAL));
        // L'originale n'est pas touchee : c'est une SECONDE ligne qui part.
        assertThat(original.payload().get("nom")).isEqualTo("N'Guessan");
        assertThat(original.anomalies()).isEmpty();
    }

    @Test
    void apostropheEnEspacePuisEnTiret() {
        assertThat(doublon(original("N'Guessan", "Awa"), 1).payload().get("nom")).isEqualTo("N Guessan");
        assertThat(doublon(original("N'Guessan", "Awa"), 2).payload().get("nom")).isEqualTo("N-Guessan");
    }

    @Test
    void espaceDuNomEnTiret() {
        assertThat(doublon(original("Kone Bakary", "Awa"), 3).payload().get("nom")).isEqualTo("Kone-Bakary");
    }

    @Test
    void prenomsPermutes() {
        SimEvent doublon = doublon(original("Kouassi", "Jean Baptiste"), 4);

        assertThat(doublon.payload().get("prenoms")).isEqualTo("Baptiste Jean");
        assertThat(doublon.payload().get("nom")).isEqualTo("Kouassi");
        assertThat(doublon.anomalies()).containsExactly(new AnomalieTracee("DOUBLON_APPROXIMATIF", "prenoms",
                "Jean Baptiste", "Baptiste Jean", ORIGINAL));
    }

    @Test
    void variationDeFinale() {
        assertThat(doublon(original("Kouassi", "Awa"), 5).payload().get("nom")).isEqualTo("Kouassy");
        assertThat(doublon(original("Bony", "Awa"), 5).payload().get("nom")).isEqualTo("Boni");
        assertThat(doublon(original("Konan", "Awa"), 5).payload().get("nom")).isEqualTo("Konann");
    }

    @Test
    void laVariationSuivanteEstEssayeeQuandCelleTireeEstImpossible() {
        // Tirage 0 (retirer l'apostrophe) sur un nom sans apostrophe : on passe a 1, 2, 3 (pas d'espace), puis
        // 4 (permuter des prenoms qui n'en font qu'un), et c'est la variation de finale qui tombe.
        assertThat(doublon(original("Kouassi", "Awa"), 0).payload().get("nom")).isEqualTo("Kouassy");
    }

    @Test
    void laLigneDeTropAToujoursSonPropreIdentifiantEtGardeLeReste() {
        SimEvent original = original("N'Guessan", "Awa");

        SimEvent doublon = mutator.muter(original, new SeededRandom(7)).orElseThrow();

        assertThat(doublon.eventId()).isNotEqualTo(ORIGINAL);
        assertThat(doublon.runId()).isEqualTo(original.runId());
        assertThat(doublon.type()).isEqualTo("FACTURE_OUVERTE");
        assertThat(doublon.timestampSimule()).isEqualTo(LocalDateTime.of(2026, 3, 10, 8, 30));
        assertThat(doublon.payload().get("quantite")).isEqualTo(2);
        assertThat(doublon.anomalies()).singleElement()
                .extracting(AnomalieTracee::doublonDe).isEqualTo(ORIGINAL);
    }

    @Test
    void rienAFaireSansNomNiPrenoms() {
        SimEvent sansNom = new SimEvent(ORIGINAL, new UUID(1, 1), "prestations", "FACTURE_OUVERTE",
                LocalDateTime.of(2026, 3, 10, 8, 30), Map.of("quantite", 2), List.of());

        assertThat(mutator.muter(sansNom, new SeededRandom(7))).isEmpty();
    }
}

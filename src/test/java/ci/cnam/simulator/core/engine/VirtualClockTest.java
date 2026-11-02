package ci.cnam.simulator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class VirtualClockTest {

    private static final Instant ORIGINE = Instant.parse("2026-03-10T08:00:00Z");

    private final AtomicLong nanos = new AtomicLong();

    private void ecouler(Duration duree) {
        nanos.addAndGet(duree.toNanos());
    }

    private VirtualClock horloge(double facteur) {
        return new VirtualClock(ORIGINE, facteur, nanos::get);
    }

    @Test
    void aX100UneMinuteReelleCouvreCentMinutesSimulees() {
        VirtualClock h = horloge(100);
        h.demarrer();
        ecouler(Duration.ofMinutes(1));

        assertThat(Duration.between(ORIGINE, h.instant())).isEqualTo(Duration.ofMinutes(100));
    }

    @Test
    void aX1EtX10LeTempsSimuleSuitLeFacteur() {
        VirtualClock x1 = horloge(1);
        VirtualClock x10 = horloge(10);
        x1.demarrer();
        x10.demarrer();
        ecouler(Duration.ofMinutes(2));

        assertThat(Duration.between(ORIGINE, x1.instant())).isEqualTo(Duration.ofMinutes(2));
        assertThat(Duration.between(ORIGINE, x10.instant())).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void laPauseFigeLeTempsEtLaRepriseRepartDeLa() {
        VirtualClock h = horloge(10);
        h.demarrer();
        ecouler(Duration.ofSeconds(30));
        h.pause();
        Instant figee = h.instant();

        ecouler(Duration.ofMinutes(10));
        assertThat(h.instant()).isEqualTo(figee);
        assertThat(h.tempsActif()).isEqualTo(Duration.ofSeconds(30));

        h.reprendre();
        ecouler(Duration.ofSeconds(30));
        assertThat(Duration.between(ORIGINE, h.instant())).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void avantLeDemarrageLeTempsNeBougePas() {
        VirtualClock h = horloge(100);
        ecouler(Duration.ofMinutes(5));

        assertThat(h.instant()).isEqualTo(ORIGINE);
    }

    @Test
    void estUnClockStandardUtilisablePourLesEtapes() {
        VirtualClock h = horloge(100);
        h.demarrer();
        ecouler(Duration.ofMinutes(1));

        assertThat(LocalDateTime.now(h)).isEqualTo(LocalDateTime.of(2026, 3, 10, 9, 40));
        assertThat(LocalDateTime.now(h.withZone(java.time.ZoneOffset.ofHours(1))))
                .isEqualTo(LocalDateTime.of(2026, 3, 10, 10, 40));
    }

    @Test
    void horlogeFigeeDateUnParcoursSelonSonInstantPrevu() {
        VirtualClock h = horloge(60);

        assertThat(LocalDateTime.now(h.figeeA(Duration.ofMinutes(2)))).isEqualTo(LocalDateTime.of(2026, 3, 10, 10, 0));
    }

    @Test
    void refuseUnFacteurNonPositif() {
        assertThatThrownBy(() -> horloge(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

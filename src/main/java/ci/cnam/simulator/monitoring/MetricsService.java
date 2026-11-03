package ci.cnam.simulator.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Metriques d'ecriture : evenements ecrits (compteur, et debit instantane en evenements/s), taille de la file,
 * duree des lots. Prometheus derive aussi le debit du compteur avec rate().
 */
@Service
public class MetricsService {

    private static final long FENETRE_DEBIT_NANOS = 1_000_000_000L;

    private final Counter ecrits;
    private final Timer dureeLots;
    private final Counter lotsEnEchec;
    private final MeterRegistry registre;

    private final Object verrouDebit = new Object();
    private long debutFenetre = System.nanoTime();
    private long ecritsFenetre;
    private volatile double debit;

    public MetricsService(MeterRegistry registre) {
        this.registre = registre;
        this.ecrits = Counter.builder("simulateur.evenements.ecrits")
                .description("Evenements ecrits en base").register(registre);
        this.lotsEnEchec = Counter.builder("simulateur.lots.echecs")
                .description("Tentatives d'ecriture de lot en echec").register(registre);
        this.dureeLots = Timer.builder("simulateur.lot.duree")
                .description("Duree d'ecriture d'un lot (transaction comprise)")
                .publishPercentileHistogram().register(registre);
        Gauge.builder("simulateur.evenements.debit", () -> debit)
                .description("Evenements ecrits par seconde (fenetre d'une seconde)").register(registre);
    }

    /** Declare la file dont on suit la taille. */
    public void surveillerFile(Supplier<Number> taille) {
        Gauge.builder("simulateur.file.taille", taille)
                .description("Evenements en attente d'ecriture").register(registre);
    }

    public void lotEcrit(int evenements, Duration duree) {
        ecrits.increment(evenements);
        dureeLots.record(duree);
        synchronized (verrouDebit) {
            ecritsFenetre += evenements;
            long maintenant = System.nanoTime();
            long ecoule = maintenant - debutFenetre;
            if (ecoule >= FENETRE_DEBIT_NANOS) {
                debit = ecritsFenetre * 1e9 / ecoule;
                ecritsFenetre = 0;
                debutFenetre = maintenant;
            }
        }
    }

    public void lotEnEchec() {
        lotsEnEchec.increment();
    }
}

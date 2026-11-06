package ci.cnam.simulator.monitoring;

import ci.cnam.simulator.chaos.ToxiproxyClient;
import ci.cnam.simulator.monitoring.EtatTableauDeBord.ProxyVue;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Etat de la base et de la liaison (Toxiproxy), sonde EN ARRIERE-PLAN : quand le proxy est coupe, obtenir une
 * connexion peut durer plusieurs secondes, et le tableau de bord ne doit jamais attendre. {@link #instantane()}
 * rend la derniere valeur connue et relance une sonde si elle a plus de {@value #VALIDITE_MS} ms.
 */
@Service
public class InfrastructureEtat {

    private static final Logger LOG = LoggerFactory.getLogger(InfrastructureEtat.class);
    static final long VALIDITE_MS = 2_000;

    /** @param base UP ou DOWN */
    public record Instantane(String base, ProxyVue proxy) {
    }

    private final DataSource source;
    private final ToxiproxyClient toxiproxy;
    private final AtomicBoolean enCours = new AtomicBoolean();
    private final ExecutorService sonde = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sonde-infrastructure");
        t.setDaemon(true);
        return t;
    });
    private volatile Instantane courant = new Instantane("INCONNU", new ProxyVue("INCONNU", List.of()));
    private volatile long derniereSondeNanos = System.nanoTime() - VALIDITE_MS * 2_000_000;

    public InfrastructureEtat(DataSource source, ToxiproxyClient toxiproxy) {
        this.source = source;
        this.toxiproxy = toxiproxy;
    }

    public Instantane instantane() {
        boolean perime = System.nanoTime() - derniereSondeNanos > VALIDITE_MS * 1_000_000;
        if (perime && enCours.compareAndSet(false, true)) {
            sonde.execute(() -> {
                try {
                    courant = new Instantane(sonderBase(), sonderProxy());
                } finally {
                    derniereSondeNanos = System.nanoTime();
                    enCours.set(false);
                }
            });
        }
        return courant;
    }

    private String sonderBase() {
        try (Connection c = source.getConnection()) {
            return c.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            LOG.debug("Base injoignable : {}", e.getMessage());
            return "DOWN";
        }
    }

    private ProxyVue sonderProxy() {
        try {
            String etat = toxiproxy.proxyActif() ? "ACTIF" : "COUPE";
            return new ProxyVue(etat, toxiproxy.toxics());
        } catch (Exception e) {
            LOG.debug("Toxiproxy injoignable : {}", e.getMessage());
            return new ProxyVue("INJOIGNABLE", List.of());
        }
    }

    @PreDestroy
    void arreter() {
        sonde.shutdownNow();
    }
}

package ci.cnam.simulator.chaos;

import ci.cnam.simulator.core.event.SimEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Etat du chaos MESSAGE_TRONQUE : tant qu'il est actif, une proportion des evenements perd la moitie de ses champs
 * (le lien avec la facture, {@code factureId}, est conserve pour que la ligne reste rattachee a son parcours).
 * Le choix est fait a partir de l'identifiant de l'evenement : pas d'alea cache, memes evenements touches a chaque fois.
 */
@Component
public class TroncatureMessages {

    private static final String CLE_LIEN = "factureId";

    private volatile double proportion;
    private final AtomicLong tronques = new AtomicLong();

    public void activer(double proportionTronquee) {
        tronques.set(0);
        proportion = proportionTronquee;
    }

    /** Arrete la troncature et rend le nombre d'evenements touches. */
    public long desactiver() {
        proportion = 0;
        return tronques.get();
    }

    public boolean actif() {
        return proportion > 0;
    }

    public long tronques() {
        return tronques.get();
    }

    /** L'evenement tel qu'il sera ecrit : tronque s'il est tire, inchange sinon. */
    public SimEvent appliquer(SimEvent evenement) {
        double p = proportion;
        if (p <= 0 || Math.floorMod(evenement.eventId().hashCode(), 1000) >= p * 1000) {
            return evenement;
        }
        Map<String, Object> source = evenement.payload();
        if (source == null || source.size() < 2) {
            return evenement;
        }
        List<String> cles = new ArrayList<>(source.keySet());
        cles.remove(CLE_LIEN);
        cles.sort(String::compareTo);
        Map<String, Object> garde = new LinkedHashMap<>();
        if (source.containsKey(CLE_LIEN)) {
            garde.put(CLE_LIEN, source.get(CLE_LIEN));
        }
        for (String cle : cles.subList(0, cles.size() / 2)) {
            garde.put(cle, source.get(cle));
        }
        tronques.incrementAndGet();
        return new SimEvent(evenement.eventId(), evenement.runId(), evenement.domaine(), evenement.type(),
                evenement.timestampSimule(), garde, evenement.anomalies());
    }
}

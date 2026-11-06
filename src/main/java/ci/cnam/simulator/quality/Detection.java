package ci.cnam.simulator.quality;

import ci.cnam.simulator.anomaly.AnomalyType;
import java.util.UUID;

/** Une detection de l'outil qualite : evenement, type, champ (nul pour un doublon), regle et score facultatifs. */
public record Detection(UUID eventId, AnomalyType type, String champ, String regle, Double score) {
}

package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.domain.prestations.facture.Facture;

/** Facture apres l'etape et evenement emis. */
public record ResultatEtape(Facture facture, SimEvent evenement) {
}

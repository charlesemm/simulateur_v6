package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;

/** Une etape du parcours : fait avancer le patient, met la facture a jour et produit un evenement. */
public interface Etape {

    ParcoursEtape code();

    ResultatEtape executer(ParcoursContext contexte);
}

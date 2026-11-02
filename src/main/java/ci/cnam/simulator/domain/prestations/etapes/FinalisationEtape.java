package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Finalisation de la facture (statut FINALISEE, etat terminal). */
@Component
public class FinalisationEtape implements Etape {

    private final GestionFacture factures;

    public FinalisationEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.FINALISATION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(1, 10);
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.FINALISEE, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.FACTURE_FINALISEE,
                Map.of("numeroFacture", facture.numeroFacture())));
    }
}

package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Retour chez le medecin : consultation de suivi (une ligne fictive) et prescription, facture PRESCRITE. */
@Component
public class PrescriptionEtape implements Etape {

    private final GestionFacture factures;

    public PrescriptionEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.PRESCRIPTION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(10, 30);
        factures.ajouterLigne(ctx.facture().id(), LigneFictive.de("CONS-SUIVI", "Consultation de suivi",
                ctx.temps(), 1, "3000.00", "2100.00", "300.00", "600.00"));
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.PRESCRITE, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.PRESCRIPTION_EMISE,
                Map.of("medecinId", ctx.medecin().id())));
    }
}

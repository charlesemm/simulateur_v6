package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Consultation initiale : une ligne (montants fictifs), facture EN_COURS. */
@Component
public class ConsultationEtape implements Etape {

    private final GestionFacture factures;

    public ConsultationEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.CONSULTATION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(10, 30);
        factures.ajouterLigne(ctx.facture().id(), LigneFictive.de("CONS", "Consultation", ctx.temps(), 1,
                "5000.00", "3500.00", "500.00", "1000.00"));
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.EN_COURS, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.CONSULTATION_REALISEE,
                Map.of("medecinId", ctx.medecin().id())));
    }
}

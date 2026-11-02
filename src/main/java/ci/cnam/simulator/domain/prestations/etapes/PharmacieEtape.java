package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Delivrance des medicaments : une ligne (1 a 3 boites, montants fictifs), facture MEDICAMENTS_DELIVRES. */
@Component
public class PharmacieEtape implements Etape {

    private final GestionFacture factures;

    public PharmacieEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.PHARMACIE;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        int boites = ctx.random().entre(1, 3);
        ctx.avancer(15, 60);
        factures.ajouterLigne(ctx.facture().id(), LigneFictive.de("MEDIC", "Medicaments", ctx.temps(), boites,
                "2000.00", "1400.00", "200.00", "400.00"));
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.MEDICAMENTS_DELIVRES, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.MEDICAMENTS_DELIVRES,
                Map.of("pharmacieId", ctx.pharmacie().id(), "quantite", boites)));
    }
}

package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.facture.NouvelleFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Ouvre la facture (statut OUVERTE) ; le type de bon vient de la voie du parcours (regle simplifiee, a valider). */
@Component
public class OuvertureFactureEtape implements Etape {

    private final GestionFacture factures;

    public OuvertureFactureEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.OUVERTURE_FACTURE;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(1, 5);
        Facture facture = factures.ouvrir(new NouvelleFacture(ctx.runId(), ctx.assure().id(), ctx.assure().id(),
                ctx.centre().id(), ctx.typeBon(), ctx.temps()));
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.FACTURE_OUVERTE,
                Map.of("numeroFacture", facture.numeroFacture(), "typeBon", facture.typeBon().code())));
    }
}

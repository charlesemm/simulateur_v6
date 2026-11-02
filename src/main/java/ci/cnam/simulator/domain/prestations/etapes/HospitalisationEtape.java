package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Hospitalisation : une ligne par jour de sejour (1 a 5 jours, montants fictifs). La facture reste EN_COURS. */
@Component
public class HospitalisationEtape implements Etape {

    private final GestionFacture factures;

    public HospitalisationEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.HOSPITALISATION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        int jours = ctx.random().entre(1, 5);
        ctx.avancer(60, 180);
        factures.ajouterLigne(ctx.facture().id(), LigneFictive.de("HOSP", "Journee d'hospitalisation", ctx.temps(),
                jours, "25000.00", "20000.00", "2500.00", "2500.00"));
        return new ResultatEtape(ctx.facture(),
                ctx.evenement(PrestationEventType.HOSPITALISATION_REALISEE, Map.of("joursSejour", jours)));
    }
}

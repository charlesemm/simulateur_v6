package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import ci.cnam.simulator.domain.prestations.tarification.TarificationService;
import ci.cnam.simulator.domain.prestations.tarification.TypePrestation;
import ci.cnam.simulator.domain.reference.catalog.Acte;
import ci.cnam.simulator.domain.reference.catalog.Acte.CategorieActe;
import ci.cnam.simulator.domain.reference.catalog.Catalogue;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Retour chez le medecin : consultation de suivi (tarif du catalogue) et prescription, facture PRESCRITE. */
@Component
public class PrescriptionEtape implements Etape {

    private final GestionFacture factures;
    private final Catalogue catalogue;
    private final TarificationService tarification;

    public PrescriptionEtape(GestionFacture factures, Catalogue catalogue, TarificationService tarification) {
        this.factures = factures;
        this.catalogue = catalogue;
        this.tarification = tarification;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.PRESCRIPTION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(10, 30);
        Acte acte = catalogue.acteAleatoire(CategorieActe.CONSULTATION, ctx.random());
        factures.ajouterLigne(ctx.facture().id(), tarification.ligne(acte.code(), acte.libelle(),
                ctx.temps(), 1, acte.tarif(), TypePrestation.CONSULTATION));
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.PRESCRITE, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.PRESCRIPTION_EMISE,
                Map.of("medecinId", ctx.medecin().id())));
    }
}

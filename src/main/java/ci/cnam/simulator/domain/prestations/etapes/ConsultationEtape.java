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

/** Consultation initiale : une ligne au tarif du catalogue, facture EN_COURS. */
@Component
public class ConsultationEtape implements Etape {

    private final GestionFacture factures;
    private final Catalogue catalogue;
    private final TarificationService tarification;

    public ConsultationEtape(GestionFacture factures, Catalogue catalogue, TarificationService tarification) {
        this.factures = factures;
        this.catalogue = catalogue;
        this.tarification = tarification;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.CONSULTATION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(10, 30);
        Acte acte = catalogue.acteAleatoire(CategorieActe.CONSULTATION, ctx.random());
        factures.ajouterLigne(ctx.facture().id(), tarification.ligne(acte.code(), acte.libelle(), ctx.temps(), 1,
                acte.tarif(), TypePrestation.CONSULTATION));
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.EN_COURS, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.CONSULTATION_REALISEE,
                Map.of("medecinId", ctx.medecin().id())));
    }
}

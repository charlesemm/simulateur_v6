package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
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

/** Hospitalisation : une ligne de 1 a 5 jours au tarif du catalogue. La facture reste EN_COURS. */
@Component
public class HospitalisationEtape implements Etape {

    private final GestionFacture factures;
    private final Catalogue catalogue;
    private final TarificationService tarification;

    public HospitalisationEtape(GestionFacture factures, Catalogue catalogue, TarificationService tarification) {
        this.factures = factures;
        this.catalogue = catalogue;
        this.tarification = tarification;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.HOSPITALISATION;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        int jours = ctx.random().entre(1, 5);
        ctx.avancer(60, 180);
        Acte acte = catalogue.acteAleatoire(CategorieActe.HOSPITALISATION, ctx.random());
        factures.ajouterLigne(ctx.facture().id(), tarification.ligne(acte.code(), acte.libelle(), ctx.temps(),
                jours, acte.tarif(), TypePrestation.HOSPITALISATION));
        return new ResultatEtape(ctx.facture(),
                ctx.evenement(PrestationEventType.HOSPITALISATION_REALISEE, Map.of("joursSejour", jours)));
    }
}

package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import ci.cnam.simulator.domain.prestations.tarification.TarificationService;
import ci.cnam.simulator.domain.prestations.tarification.TypePrestation;
import ci.cnam.simulator.domain.reference.catalog.Catalogue;
import ci.cnam.simulator.domain.reference.catalog.Medicament;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Delivrance des medicaments : une ligne de 1 a 3 boites au tarif du catalogue, facture MEDICAMENTS_DELIVRES. */
@Component
public class PharmacieEtape implements Etape {

    private final GestionFacture factures;
    private final Catalogue catalogue;
    private final TarificationService tarification;

    public PharmacieEtape(GestionFacture factures, Catalogue catalogue, TarificationService tarification) {
        this.factures = factures;
        this.catalogue = catalogue;
        this.tarification = tarification;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.PHARMACIE;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        int boites = ctx.random().entre(1, 3);
        ctx.avancer(15, 60);
        Medicament medicament = catalogue.medicamentAleatoire(ctx.random());
        factures.ajouterLigne(ctx.facture().id(), tarification.ligne(medicament.code(), medicament.libelle(),
                ctx.temps(), boites, medicament.tarif(), TypePrestation.MEDICAMENT));
        Facture facture = factures.changerStatut(ctx.facture().id(), FactureStatut.MEDICAMENTS_DELIVRES, ctx.temps());
        ctx.facture(facture);
        return new ResultatEtape(facture, ctx.evenement(PrestationEventType.MEDICAMENTS_DELIVRES,
                Map.of("pharmacieId", ctx.pharmacie().id(), "quantite", boites)));
    }
}

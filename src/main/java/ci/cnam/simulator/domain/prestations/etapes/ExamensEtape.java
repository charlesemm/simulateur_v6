package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import ci.cnam.simulator.domain.prestations.tarification.TarificationService;
import ci.cnam.simulator.domain.prestations.tarification.TypePrestation;
import ci.cnam.simulator.domain.reference.catalog.Catalogue;
import ci.cnam.simulator.domain.reference.catalog.Examen;
import ci.cnam.simulator.domain.reference.catalog.Examen.TypeExamen;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Examens de biologie et d'imagerie : deux lignes aux tarifs du catalogue. La facture reste EN_COURS. */
@Component
public class ExamensEtape implements Etape {

    private final GestionFacture factures;
    private final Catalogue catalogue;
    private final TarificationService tarification;

    public ExamensEtape(GestionFacture factures, Catalogue catalogue, TarificationService tarification) {
        this.factures = factures;
        this.catalogue = catalogue;
        this.tarification = tarification;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.EXAMENS;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(30, 120);
        for (TypeExamen type : TypeExamen.values()) {
            Examen examen = catalogue.examenAleatoire(type, ctx.random());
            factures.ajouterLigne(ctx.facture().id(), tarification.ligne(examen.code(), examen.libelle(),
                    ctx.temps(), 1, examen.tarif(), TypePrestation.EXAMEN, ctx.assure().regime()));
        }
        return new ResultatEtape(ctx.facture(),
                ctx.evenement(PrestationEventType.EXAMEN_REALISE, Map.of("nombreExamens", 2)));
    }
}

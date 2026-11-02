package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Examens de biologie et d'imagerie : deux lignes (montants fictifs). La facture reste EN_COURS. */
@Component
public class ExamensEtape implements Etape {

    private final GestionFacture factures;

    public ExamensEtape(GestionFacture factures) {
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.EXAMENS;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(30, 120);
        factures.ajouterLigne(ctx.facture().id(), LigneFictive.de("BIO", "Bilan biologique", ctx.temps(), 1,
                "12000.00", "8400.00", "1200.00", "2400.00"));
        factures.ajouterLigne(ctx.facture().id(), LigneFictive.de("IMG", "Imagerie", ctx.temps(), 1,
                "20000.00", "14000.00", "2000.00", "4000.00"));
        return new ResultatEtape(ctx.facture(),
                ctx.evenement(PrestationEventType.EXAMEN_REALISE, Map.of("nombreExamens", 2)));
    }
}

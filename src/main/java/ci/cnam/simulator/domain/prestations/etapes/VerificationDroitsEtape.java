package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.droits.ControleDroitsService;
import ci.cnam.simulator.domain.prestations.droits.ResultatDroits;
import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.facture.NouvelleFacture;
import ci.cnam.simulator.domain.prestations.facture.TypeBon;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursEtape;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Droits ouverts a la date de soin et cotisations a jour. En cas d'echec : facture REFUSEE creee directement,
 * evenement ACCES_REFUSE et fin du parcours. En cas de succes, aucune facture n'existe encore : l'evenement
 * DROITS_VERIFIES porte un factureId nul (la facture s'ouvre a l'etape suivante).
 */
@Component
public class VerificationDroitsEtape implements Etape {

    private final ControleDroitsService controle;
    private final GestionFacture factures;

    public VerificationDroitsEtape(ControleDroitsService controle, GestionFacture factures) {
        this.controle = controle;
        this.factures = factures;
    }

    @Override
    public ParcoursEtape code() {
        return ParcoursEtape.VERIFICATION_DROITS;
    }

    @Override
    public ResultatEtape executer(ParcoursContext ctx) {
        ctx.avancer(1, 5);
        ResultatDroits resultat = controle.controler(ctx.assure(), ctx.temps().toLocalDate());
        if (resultat.accorde()) {
            return new ResultatEtape(null, ctx.evenement(PrestationEventType.DROITS_VERIFIES, Map.of()));
        }
        ctx.refuserAcces();
        // Facture refusee : jamais ouverte, donc type de bon par defaut (ambulatoire).
        Facture refusee = factures.refuser(new NouvelleFacture(ctx.runId(), ctx.assure().id(), ctx.assure().id(),
                ctx.centre().id(), TypeBon.AMBULATOIRE, ctx.temps()));
        ctx.facture(refusee);
        return new ResultatEtape(refusee,
                ctx.evenement(PrestationEventType.ACCES_REFUSE, Map.of("motif", resultat.motif().name())));
    }
}

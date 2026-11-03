package ci.cnam.simulator.domain.prestations;

import ci.cnam.simulator.anomaly.AnomalyInjector;
import ci.cnam.simulator.anomaly.InjectionSink;
import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.core.engine.ContexteGeneration;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import ci.cnam.simulator.domain.FlowGenerator;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursOrchestrator;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursProperties;
import ci.cnam.simulator.domain.reference.ReferenceService;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.sink.EventSink;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Generateur du domaine "prestations" : tire les acteurs du referentiel puis deroule UN parcours complet.
 * Les cles de parcours du scenario sont : examens, hospitalisation, prescriptionDirecte (en %).
 */
@Component
public class PrestationsGenerator implements FlowGenerator {

    static final String EXAMENS = "examens";
    static final String HOSPITALISATION = "hospitalisation";
    static final String PRESCRIPTION_DIRECTE = "prescriptionDirecte";
    private static final Set<String> CLES = Set.of(EXAMENS, HOSPITALISATION, PRESCRIPTION_DIRECTE);

    private final ParcoursOrchestrator orchestrateur;
    private final ReferenceService reference;
    private final ParcoursProperties parDefaut;
    private final AnomalyInjector injecteur;

    public PrestationsGenerator(ParcoursOrchestrator orchestrateur, ReferenceService reference,
                                ParcoursProperties parDefaut, AnomalyInjector injecteur) {
        this.orchestrateur = orchestrateur;
        this.reference = reference;
        this.parDefaut = parDefaut;
        this.injecteur = injecteur;
    }

    @Override
    public String domaine() {
        return ParcoursContext.DOMAINE;
    }

    @Override
    public void valider(ConfigDomaine config) {
        AnomalyProfile.depuis(config.anomalies());   // leve IllegalArgumentException si un taux est invalide
        Map<String, Double> p = config.parcours();
        if (p.isEmpty()) {
            return;
        }
        if (!p.keySet().equals(CLES)) {
            throw new IllegalArgumentException("domaines.prestations.parcours : les trois cles " + CLES
                    + " sont attendues (cles lues : " + p.keySet() + ")");
        }
    }

    @Override
    public void generer(ContexteGeneration ctx) {
        SeededRandom random = ctx.random();
        Assure assure = reference.assureAleatoire(random);
        CentreSante centre = reference.centreAleatoire(random);
        orchestrateur.executer(ctx.runId(), assure, centre, reference.medecinAleatoire(centre, random),
                reference.pharmacieAleatoire(random), random, ctx.horloge(), probabilites(ctx.config()),
                destination(ctx));
    }

    /** Le parcours reste propre : seule la sortie est enveloppee par l'injecteur, et seulement si un taux est > 0. */
    private EventSink destination(ContexteGeneration ctx) {
        AnomalyProfile profil = AnomalyProfile.depuis(ctx.config().anomalies());
        return profil.actif() ? new InjectionSink(ctx.sink(), injecteur, profil) : ctx.sink();
    }

    private ParcoursProperties probabilites(ConfigDomaine config) {
        Map<String, Double> p = config.parcours();
        if (p.isEmpty()) {
            return parDefaut;
        }
        return new ParcoursProperties(p.get(EXAMENS) / 100.0, p.get(HOSPITALISATION) / 100.0,
                p.get(PRESCRIPTION_DIRECTE) / 100.0);
    }
}

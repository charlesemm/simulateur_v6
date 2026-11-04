package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.etapes.Etape;
import ci.cnam.simulator.domain.prestations.etapes.ResultatEtape;
import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import ci.cnam.simulator.sink.EcritureAnnexe;
import ci.cnam.simulator.sink.EventSink;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deroule le parcours d'un patient, etape par etape, selon le {@link ParcoursGraph}. Chaque evenement est
 * envoye a l'{@link EventSink}. Le temps de depart vient de l'horloge injectee (jamais Instant.now()).
 */
@Component
public class ParcoursOrchestrator {

    /** Parcours termine : facture finale (FINALISEE ou REFUSEE) et evenements dans l'ordre d'emission. */
    public record Resultat(Facture facture, List<SimEvent> evenements) {
    }

    private final Map<ParcoursEtape, Etape> etapes = new EnumMap<>(ParcoursEtape.class);
    private final ParcoursGraph graphe;
    private final GestionFacture factures;
    private final EventSink sink;
    private final Clock horloge;

    public ParcoursOrchestrator(List<Etape> etapes, ParcoursGraph graphe, GestionFacture factures, EventSink sink,
                               Clock horloge) {
        etapes.forEach(e -> this.etapes.put(e.code(), e));
        for (ParcoursEtape code : ParcoursEtape.values()) {
            if (!this.etapes.containsKey(code)) {
                throw new IllegalStateException("Etape manquante : " + code);
            }
        }
        this.graphe = graphe;
        this.factures = factures;
        this.sink = sink;
        this.horloge = horloge;
    }

    public Resultat executer(UUID runId, Assure assure, CentreSante centre, Medecin medecin, Pharmacie pharmacie,
                             SeededRandom random) {
        return executer(runId, assure, centre, medecin, pharmacie, random, horloge, graphe.proprietes(), sink);
    }

    /**
     * Parcours date par {@code horlogeParcours} (ex. horloge virtuelle du moteur), avec des probabilites de voie
     * et une destination d'evenements propres au run.
     */
    public Resultat executer(UUID runId, Assure assure, CentreSante centre, Medecin medecin, Pharmacie pharmacie,
                             SeededRandom random, Clock horlogeParcours, ParcoursProperties probas,
                             EventSink destination) {
        // La voie est tiree a l'arrivee (le type de bon en depend a l'ouverture) ; le graphe l'applique apres la consultation.
        VoieParcours voie = graphe.tirerVoie(random, probas);
        ParcoursContext ctx = new ParcoursContext(runId, assure, centre, medecin, pharmacie, random, voie,
                LocalDateTime.now(horlogeParcours));
        List<SimEvent> evenements = new ArrayList<>();
        emettre(destination, ctx.evenement(PrestationEventType.PATIENT_ARRIVE, Map.of()), evenements);

        Optional<EcritureAnnexe> aEcrire = Optional.empty();
        try {
            Optional<ParcoursEtape> courante = Optional.of(ParcoursEtape.VERIFICATION_DROITS);
            while (courante.isPresent()) {
                ResultatEtape resultat = etapes.get(courante.get()).executer(ctx);
                if (resultat.facture() != null) {
                    ctx.facture(resultat.facture());
                }
                emettre(destination, resultat.evenement(), evenements);
                courante = graphe.suivante(courante.get(), ctx);
            }
        } finally {
            // Toujours clore : un parcours en erreur ne laisse pas de facture a moitie suivie en memoire.
            if (ctx.facture() != null) {
                aEcrire = factures.clore(ctx.facture().id());
            }
        }
        aEcrire.ifPresent(destination::joindre);
        return new Resultat(ctx.facture(), List.copyOf(evenements));
    }

    private static void emettre(EventSink destination, SimEvent evenement, List<SimEvent> evenements) {
        destination.emettre(evenement);
        evenements.add(evenement);
    }
}

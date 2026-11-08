package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.events.PrestationEventType;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.TypeBon;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Etat d'un parcours en cours : les acteurs, la facture courante et l'horloge simulee.
 * Un contexte = un patient, utilise par un seul fil d'execution.
 */
public class ParcoursContext {

    public static final String DOMAINE = "prestations";

    private final UUID runId;
    private final Assure assure;
    private final CentreSante centre;
    private final Medecin medecin;
    private final Pharmacie pharmacie;
    private final SeededRandom random;
    private final VoieParcours voie;
    private LocalDateTime temps;
    private Facture facture;
    private boolean accesRefuse;
    private Boolean droitsOuverts;

    public ParcoursContext(UUID runId, Assure assure, CentreSante centre, Medecin medecin, Pharmacie pharmacie,
                           SeededRandom random, VoieParcours voie, LocalDateTime debut) {
        this.runId = runId;
        this.assure = assure;
        this.centre = centre;
        this.medecin = medecin;
        this.pharmacie = pharmacie;
        this.random = random;
        this.voie = voie;
        this.temps = debut;
    }

    public UUID runId() {
        return runId;
    }

    /** Le patient. Les ayants droit ne sont pas encore simules : le beneficiaire est l'assure lui-meme. */
    public Assure assure() {
        return assure;
    }

    public CentreSante centre() {
        return centre;
    }

    public Medecin medecin() {
        return medecin;
    }

    public Pharmacie pharmacie() {
        return pharmacie;
    }

    public SeededRandom random() {
        return random;
    }

    /** Voie decidee a l'arrivee du patient, appliquee par le graphe apres la consultation. */
    public VoieParcours voie() {
        return voie;
    }

    /** Date simulee courante. */
    public LocalDateTime temps() {
        return temps;
    }

    /** Facture courante ; null tant qu'elle n'existe pas (avant l'ouverture ou le refus). */
    public Facture facture() {
        return facture;
    }

    public void facture(Facture facture) {
        this.facture = facture;
    }

    public boolean accesRefuse() {
        return accesRefuse;
    }

    public void refuserAcces() {
        this.accesRefuse = true;
    }

    /**
     * Resultat du controle des droits, une fois qu'il a eu lieu ; null avant. Porte dans les evenements qui
     * suivent sous la cle {@code droitsOuverts} : c'est contre lui que se juge l'incoherence croisee.
     */
    public Boolean droitsOuverts() {
        return droitsOuverts;
    }

    public void droitsOuverts(boolean ouverts) {
        this.droitsOuverts = ouverts;
    }

    /**
     * Type de bon selon la voie. REGLE SIMPLIFIEE, A VALIDER avec les directions metier :
     * hospitalisation si sejour, biologie/imagerie si examens, ambulatoire sinon. Le bon dentaire n'est pas
     * encore produit par le parcours.
     */
    public TypeBon typeBon() {
        return switch (voie) {
            case HOSPITALISATION -> TypeBon.HOSPITALISATION;
            case EXAMENS -> TypeBon.BIOLOGIE_IMAGERIE;
            case DIRECT -> TypeBon.AMBULATOIRE;
        };
    }

    /** Fait avancer le temps simule d'une duree tiree entre les deux bornes (minutes). */
    public LocalDateTime avancer(int minMinutes, int maxMinutes) {
        temps = temps.plusMinutes(random.entre(minMinutes, maxMinutes));
        return temps;
    }

    /** Construit un evenement du domaine, date du temps simule courant, avec le factureId s'il existe. */
    public SimEvent evenement(PrestationEventType type, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("factureId", facture == null ? null : facture.id());
        payload.put("assureId", assure.id());
        payload.put("centreSanteId", centre.id());
        // Identite du patient, telle que l'agent la saisit : c'est ce que l'injecteur d'anomalies peut corrompre.
        payload.put("numeroAssure", assure.numeroAssure());
        payload.put("nom", assure.nom());
        payload.put("prenoms", assure.prenoms());
        // Contexte temporel du soin : la date du soin et la periode de droits contre laquelle elle se juge.
        // Ces trois champs sont PROPRES ; ce sont eux que les anomalies de relation mettent en contradiction.
        payload.put("dateSoin", temps.toLocalDate().toString());
        payload.put("dateDebutDroits", assure.dateDebutDroits().toString());
        if (assure.dateFinDroits() != null) {
            payload.put("dateFinDroits", assure.dateFinDroits().toString());
        }
        if (droitsOuverts != null) {
            payload.put("droitsOuverts", droitsOuverts);
        }
        if (type == PrestationEventType.PATIENT_ARRIVE) {
            payload.put("dateNaissance", assure.dateNaissance().toString());
            payload.put("genre", assure.genre().name());
        }
        payload.putAll(details);
        return new SimEvent(random.nextUuid(), runId, DOMAINE, type.name(), temps, payload, List.of());
    }
}

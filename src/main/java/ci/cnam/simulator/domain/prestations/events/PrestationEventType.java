package ci.cnam.simulator.domain.prestations.events;

/** Types d'evenements du domaine prestations : un par etape du parcours. */
public enum PrestationEventType {
    PATIENT_ARRIVE,
    DROITS_VERIFIES,
    ACCES_REFUSE,
    FACTURE_OUVERTE,
    CONSULTATION_REALISEE,
    EXAMEN_REALISE,
    HOSPITALISATION_REALISEE,
    PRESCRIPTION_EMISE,
    MEDICAMENTS_DELIVRES,
    FACTURE_FINALISEE
}

package ci.cnam.simulator.domain.prestations.parcours;

/** Branche prise apres la consultation. */
public enum VoieParcours {
    EXAMENS,
    HOSPITALISATION,
    /** Sans examen : directement a la prescription. */
    DIRECT
}

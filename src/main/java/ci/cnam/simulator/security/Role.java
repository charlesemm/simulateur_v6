package ci.cnam.simulator.security;

/** Roles de l'interface. ADMIN : tout ; OPERATEUR : piloter et casser ; OBSERVATEUR : lire seulement. */
public enum Role {
    ADMIN, OPERATEUR, OBSERVATEUR;

    /** Nom d'autorite Spring Security. */
    public String autorite() {
        return "ROLE_" + name();
    }
}

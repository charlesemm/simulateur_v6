package ci.cnam.simulator.security;

import ci.cnam.simulator.persistence.entity.AppUser;

/**
 * Resultat d'une creation de compte ou d'une reinitialisation : le compte et le mot de passe temporaire EN CLAIR.
 *
 * <p>Ce mot de passe n'existe qu'ici, le temps d'etre affiche une fois a l'administrateur. Il n'est jamais
 * enregistre (seul le hash BCrypt l'est) ni inscrit au journal d'audit.</p>
 */
public record CompteCree(AppUser compte, String motDePasseTemporaire) {
}

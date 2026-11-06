package ci.cnam.simulator.security;

import ci.cnam.simulator.persistence.entity.AppUser;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Compte tel que Spring Security le voit. Photo prise a la connexion : aucune requete en base ensuite. */
public record AppUserPrincipal(
        String nomUtilisateur,
        String motDePasseHash,
        Role role,
        boolean actif,
        boolean verrouille,
        boolean motDePasseAChanger) implements UserDetails {

    public static AppUserPrincipal de(AppUser u, Instant maintenant) {
        return new AppUserPrincipal(u.getNomUtilisateur(), u.getMotDePasseHash(), u.getRole(), u.isActif(),
                u.verrouille(maintenant), u.isMotDePasseAChanger());
    }

    /** Meme compte, mot de passe desormais choisi par son titulaire. */
    public AppUserPrincipal motDePasseChange(String nouveauHash) {
        return new AppUserPrincipal(nomUtilisateur, nouveauHash, role, actif, verrouille, false);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.autorite()));
    }

    @Override
    public String getPassword() {
        return motDePasseHash;
    }

    @Override
    public String getUsername() {
        return nomUtilisateur;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !verrouille;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    /** Evite d'ecrire le hash dans un journal ou un message d'erreur. */
    @Override
    public String toString() {
        return "AppUserPrincipal[" + nomUtilisateur + ", " + role + "]";
    }
}

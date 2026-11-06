package ci.cnam.simulator.security;

import ci.cnam.simulator.persistence.repository.AppUserRepository;
import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Charge un compte de la base pour la connexion par formulaire. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository utilisateurs;
    private final Clock horloge;

    public AppUserDetailsService(AppUserRepository utilisateurs, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.horloge = horloge;
    }

    @Override
    public UserDetails loadUserByUsername(String nomUtilisateur) throws UsernameNotFoundException {
        return utilisateurs.findByNomUtilisateur(nomUtilisateur)
                .map(u -> AppUserPrincipal.de(u, horloge.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));
    }
}

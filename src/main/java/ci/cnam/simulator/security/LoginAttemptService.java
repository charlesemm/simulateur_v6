package ci.cnam.simulator.security;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.persistence.repository.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compte les echecs de connexion et verrouille temporairement le compte apres {@code echecsAvantVerrouillage}
 * echecs consecutifs. Un compte inconnu est ignore silencieusement (aucune difference visible : pas de recensement
 * des comptes). Un compte deja verrouille ne cumule pas d'echecs : Spring le refuse avant de verifier le mot de passe.
 */
@Service
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);

    private final AppUserRepository utilisateurs;
    private final AuditService audit;
    private final Clock horloge;
    private final int seuil;
    private final Duration duree;

    public LoginAttemptService(AppUserRepository utilisateurs, AuditService audit, Clock horloge,
                               SimulateurProperties proprietes) {
        this.utilisateurs = utilisateurs;
        this.audit = audit;
        this.horloge = horloge;
        this.seuil = proprietes.securite().echecsAvantVerrouillage();
        this.duree = Duration.ofMinutes(proprietes.securite().verrouillageMinutes());
    }

    @EventListener
    @Transactional
    public void echec(AuthenticationFailureBadCredentialsEvent evenement) {
        String nom = evenement.getAuthentication().getName();
        utilisateurs.findByNomUtilisateur(nom).ifPresent(u -> {
            int echecs = u.getEchecsConsecutifs() + 1;
            if (echecs >= seuil) {
                Instant jusqua = horloge.instant().plus(duree);
                u.setVerrouilleJusqua(jusqua);
                u.setEchecsConsecutifs(0);
                LOG.warn("Compte {} verrouille jusqu'a {} apres {} echecs", nom, jusqua, seuil);
                audit.enregistrer(nom, "COMPTE_VERROUILLE", nom, echecs + " echecs de connexion consecutifs");
            } else {
                u.setEchecsConsecutifs(echecs);
            }
            utilisateurs.save(u);
        });
    }

    @EventListener
    @Transactional
    public void succes(AuthenticationSuccessEvent evenement) {
        utilisateurs.findByNomUtilisateur(evenement.getAuthentication().getName()).ifPresent(u -> {
            u.setEchecsConsecutifs(0);
            u.setVerrouilleJusqua(null);
            u.setDateDerniereConnexion(horloge.instant());
            utilisateurs.save(u);
        });
    }
}

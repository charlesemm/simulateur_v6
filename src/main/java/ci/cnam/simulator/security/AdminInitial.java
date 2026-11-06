package ci.cnam.simulator.security;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.persistence.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cree le premier ADMIN au demarrage, SEULEMENT si aucun compte n'existe, avec le mot de passe de la variable
 * {@code ADMIN_INITIAL_PASSWORD}. Le changement du mot de passe est exige a la premiere connexion.
 * Le mot de passe n'est jamais ecrit dans un journal.
 */
@Component
public class AdminInitial implements ApplicationRunner {

    public static final String NOM = "admin";
    private static final Logger LOG = LoggerFactory.getLogger(AdminInitial.class);

    private final AppUserRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final AuditService audit;
    private final String motDePasse;

    public AdminInitial(AppUserRepository utilisateurs, PasswordEncoder encodeur, AuditService audit,
                        SimulateurProperties proprietes) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.audit = audit;
        this.motDePasse = proprietes.securite().motDePasseAdminInitial();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (utilisateurs.count() > 0) {
            return;
        }
        if (motDePasse == null || motDePasse.isBlank()) {
            LOG.warn("Aucun compte et ADMIN_INITIAL_PASSWORD absent : personne ne pourra se connecter a l'interface");
            return;
        }
        AppUser admin = new AppUser(NOM, encodeur.encode(motDePasse), Role.ADMIN);
        admin.setMotDePasseAChanger(true);
        utilisateurs.save(admin);
        audit.enregistrer("systeme", "UTILISATEUR_CREE", NOM, "premier administrateur");
        LOG.info("Compte '{}' cree : changement de mot de passe exige a la premiere connexion", NOM);
    }
}

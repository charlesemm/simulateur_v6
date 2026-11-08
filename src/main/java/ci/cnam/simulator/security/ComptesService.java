package ci.cnam.simulator.security;

import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.persistence.repository.AppUserRepository;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des comptes (reservee a ADMIN par les controleurs). Chaque changement est inscrit au journal d'audit,
 * sans jamais y ecrire un mot de passe.
 */
@Service
public class ComptesService {

    /** Erreur de saisie ou de regle : le message est affichable tel quel a l'utilisateur. */
    public static class ComptesException extends RuntimeException {
        public ComptesException(String message) {
            super(message);
        }
    }

    static final int LONGUEUR_MIN = 12;
    private static final Pattern NOM_VALIDE = Pattern.compile("[a-z0-9._-]{3,50}");
    /** Controle volontairement large : on verifie la forme, pas l existence de la boite. */
    private static final Pattern EMAIL_VALIDE = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}");

    private final AppUserRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final AuditService audit;

    public ComptesService(AppUserRepository utilisateurs, PasswordEncoder encodeur, AuditService audit) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.audit = audit;
    }

    public List<AppUser> lister() {
        return utilisateurs.findAllByOrderByNomUtilisateurAsc();
    }

    /**
     * Cree un compte et rend le mot de passe temporaire EN CLAIR, pour qu'il soit affiche une seule fois.
     *
     * @param email            facultatif : sert uniquement a preparer le message de remise des identifiants
     * @param motDePasseChoisi laisse vide, un mot de passe temporaire est tire au hasard
     */
    @Transactional
    public CompteCree creer(String acteur, String nom, String email, String motDePasseChoisi, Role role) {
        String nomPropre = nom == null ? "" : nom.strip().toLowerCase(java.util.Locale.ROOT);
        if (!NOM_VALIDE.matcher(nomPropre).matches()) {
            throw new ComptesException("Nom d'utilisateur : 3 a 50 caracteres parmi minuscules, chiffres, . _ -");
        }
        if (role == null) {
            throw new ComptesException("Role obligatoire");
        }
        String emailPropre = nettoyerEmail(email);
        String motDePasse = ouGenere(motDePasseChoisi);
        verifierMotDePasse(nomPropre, motDePasse);
        if (utilisateurs.findByNomUtilisateur(nomPropre).isPresent()) {
            throw new ComptesException("Ce nom d'utilisateur existe deja");
        }
        AppUser compte = new AppUser(nomPropre, encodeur.encode(motDePasse), role);
        compte.setEmail(emailPropre);
        AppUser cree = utilisateurs.save(compte);
        audit.enregistrer(acteur, "UTILISATEUR_CREE", nomPropre,
                "role " + role + (emailPropre == null ? "" : ", email " + emailPropre));
        return new CompteCree(cree, motDePasse);
    }

    /** Un mot de passe vide signifie : « tire-le pour moi ». */
    private static String ouGenere(String choisi) {
        return choisi == null || choisi.isBlank() ? MotDePasseTemporaire.generer() : choisi;
    }

    /** Rend null pour une saisie vide, sinon l'adresse en minuscules apres controle de forme. */
    private static String nettoyerEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String propre = email.strip().toLowerCase(java.util.Locale.ROOT);
        if (propre.length() > 255 || !EMAIL_VALIDE.matcher(propre).matches()) {
            throw new ComptesException("Adresse de messagerie invalide");
        }
        return propre;
    }

    /** Renseigne ou corrige l'adresse d'un compte (laisser vide l'efface). */
    @Transactional
    public void changerEmail(String acteur, UUID id, String email) {
        AppUser u = trouver(id);
        String propre = nettoyerEmail(email);
        u.setEmail(propre);
        utilisateurs.save(u);
        audit.enregistrer(acteur, "EMAIL_MODIFIE", u.getNomUtilisateur(), propre == null ? "efface" : propre);
    }

    @Transactional
    public void changerRole(String acteur, UUID id, Role nouveau) {
        AppUser u = trouver(id);
        if (nouveau == null) {
            throw new ComptesException("Role obligatoire");
        }
        if (u.getNomUtilisateur().equals(acteur)) {
            throw new ComptesException("Vous ne pouvez pas modifier votre propre role");
        }
        if (u.getRole() == Role.ADMIN && nouveau != Role.ADMIN) {
            garderUnAdmin(u);
        }
        Role ancien = u.getRole();
        u.setRole(nouveau);
        utilisateurs.save(u);
        audit.enregistrer(acteur, "ROLE_MODIFIE", u.getNomUtilisateur(), ancien + " -> " + nouveau);
    }

    @Transactional
    public void basculerActif(String acteur, UUID id) {
        AppUser u = trouver(id);
        if (u.getNomUtilisateur().equals(acteur)) {
            throw new ComptesException("Vous ne pouvez pas desactiver votre propre compte");
        }
        if (u.isActif() && u.getRole() == Role.ADMIN) {
            garderUnAdmin(u);
        }
        u.setActif(!u.isActif());
        utilisateurs.save(u);
        audit.enregistrer(acteur, u.isActif() ? "UTILISATEUR_ACTIVE" : "UTILISATEUR_DESACTIVE",
                u.getNomUtilisateur(), null);
    }

    @Transactional
    public void deverrouiller(String acteur, UUID id) {
        AppUser u = trouver(id);
        u.setVerrouilleJusqua(null);
        u.setEchecsConsecutifs(0);
        utilisateurs.save(u);
        audit.enregistrer(acteur, "COMPTE_DEVERROUILLE", u.getNomUtilisateur(), null);
    }

    /**
     * Pose un mot de passe temporaire : le titulaire devra le changer a sa prochaine connexion.
     * Rend le mot de passe EN CLAIR pour qu il soit affiche une seule fois (laisse vide, il est tire au hasard).
     */
    @Transactional
    public CompteCree reinitialiserMotDePasse(String acteur, UUID id, String motDePasseChoisi) {
        AppUser u = trouver(id);
        String motDePasse = ouGenere(motDePasseChoisi);
        verifierMotDePasse(u.getNomUtilisateur(), motDePasse);
        u.setMotDePasseHash(encodeur.encode(motDePasse));
        u.setMotDePasseAChanger(true);
        u.setVerrouilleJusqua(null);
        u.setEchecsConsecutifs(0);
        utilisateurs.save(u);
        audit.enregistrer(acteur, "MOT_DE_PASSE_REINITIALISE", u.getNomUtilisateur(), null);
        return new CompteCree(u, motDePasse);
    }

    /** Changement par le titulaire. Rend le nouveau hash (pour rafraichir la session). */
    @Transactional
    public String changerMotDePasse(String nom, String ancien, String nouveau) {
        AppUser u = utilisateurs.findByNomUtilisateur(nom)
                .orElseThrow(() -> new ComptesException("Compte inconnu"));
        if (ancien == null || !encodeur.matches(ancien, u.getMotDePasseHash())) {
            throw new ComptesException("Mot de passe actuel incorrect");
        }
        verifierMotDePasse(nom, nouveau);
        if (nouveau.equals(ancien)) {
            throw new ComptesException("Le nouveau mot de passe doit etre different de l'ancien");
        }
        u.setMotDePasseHash(encodeur.encode(nouveau));
        u.setMotDePasseAChanger(false);
        utilisateurs.save(u);
        audit.enregistrer(nom, "MOT_DE_PASSE_CHANGE", nom, null);
        return u.getMotDePasseHash();
    }

    /** Regle de robustesse minimale : longueur, et pas le nom d'utilisateur. */
    static void verifierMotDePasse(String nom, String motDePasse) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN) {
            throw new ComptesException("Mot de passe : " + LONGUEUR_MIN + " caracteres au minimum");
        }
        if (motDePasse.equalsIgnoreCase(nom)) {
            throw new ComptesException("Mot de passe : ne doit pas etre le nom d'utilisateur");
        }
    }

    private AppUser trouver(UUID id) {
        return utilisateurs.findById(id).orElseThrow(() -> new ComptesException("Compte inconnu"));
    }

    /** Refuse de retirer le dernier ADMIN actif : plus personne ne pourrait administrer. */
    private void garderUnAdmin(AppUser concerne) {
        long autres = utilisateurs.findAll().stream()
                .filter(a -> a.getRole() == Role.ADMIN && a.isActif() && !a.getId().equals(concerne.getId()))
                .count();
        if (autres == 0) {
            throw new ComptesException("Il doit rester au moins un administrateur actif");
        }
    }
}

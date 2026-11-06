package ci.cnam.simulator.persistence.entity;

import ci.cnam.simulator.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Compte de l'interface web. Le mot de passe n'existe qu'a l'etat de hash BCrypt. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "nom_utilisateur", nullable = false, unique = true)
    private String nomUtilisateur;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String motDePasseHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "mot_de_passe_a_changer", nullable = false)
    private boolean motDePasseAChanger = true;

    @Column(name = "echecs_consecutifs", nullable = false)
    private int echecsConsecutifs;

    @Column(name = "verrouille_jusqua")
    private Instant verrouilleJusqua;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation = Instant.now();

    @Column(name = "date_derniere_connexion")
    private Instant dateDerniereConnexion;

    protected AppUser() {
        // JPA
    }

    public AppUser(String nomUtilisateur, String motDePasseHash, Role role) {
        this.id = UUID.randomUUID();
        this.nomUtilisateur = nomUtilisateur;
        this.motDePasseHash = motDePasseHash;
        this.role = role;
    }

    public boolean verrouille(Instant maintenant) {
        return verrouilleJusqua != null && verrouilleJusqua.isAfter(maintenant);
    }

    public UUID getId() {
        return id;
    }

    public String getNomUtilisateur() {
        return nomUtilisateur;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public void setMotDePasseHash(String motDePasseHash) {
        this.motDePasseHash = motDePasseHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public boolean isMotDePasseAChanger() {
        return motDePasseAChanger;
    }

    public void setMotDePasseAChanger(boolean motDePasseAChanger) {
        this.motDePasseAChanger = motDePasseAChanger;
    }

    public int getEchecsConsecutifs() {
        return echecsConsecutifs;
    }

    public void setEchecsConsecutifs(int echecsConsecutifs) {
        this.echecsConsecutifs = echecsConsecutifs;
    }

    public Instant getVerrouilleJusqua() {
        return verrouilleJusqua;
    }

    public void setVerrouilleJusqua(Instant verrouilleJusqua) {
        this.verrouilleJusqua = verrouilleJusqua;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateDerniereConnexion() {
        return dateDerniereConnexion;
    }

    public void setDateDerniereConnexion(Instant dateDerniereConnexion) {
        this.dateDerniereConnexion = dateDerniereConnexion;
    }
}

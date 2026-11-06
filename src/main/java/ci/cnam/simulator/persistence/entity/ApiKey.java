package ci.cnam.simulator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cle d'API : seule l'empreinte SHA-256 est conservee, jamais la cle elle-meme. */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation = Instant.now();

    @Column(name = "derniere_utilisation")
    private Instant derniereUtilisation;

    protected ApiKey() {
        // JPA
    }

    public ApiKey(String nom, String hash) {
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.hash = hash;
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getHash() {
        return hash;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDerniereUtilisation() {
        return derniereUtilisation;
    }

    public void setDerniereUtilisation(Instant derniereUtilisation) {
        this.derniereUtilisation = derniereUtilisation;
    }
}

package ci.cnam.simulator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Une ligne du journal d'audit : qui, quoi, sur quoi, quand. Jamais de secret dans {@code details}. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant date;

    @Column(nullable = false)
    private String acteur;

    @Column(nullable = false)
    private String action;

    private String cible;

    private String details;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(Instant date, String acteur, String action, String cible, String details) {
        this.id = UUID.randomUUID();
        this.date = date;
        this.acteur = acteur;
        this.action = action;
        this.cible = cible;
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public Instant getDate() {
        return date;
    }

    public String getActeur() {
        return acteur;
    }

    public String getAction() {
        return action;
    }

    public String getCible() {
        return cible;
    }

    public String getDetails() {
        return details;
    }
}

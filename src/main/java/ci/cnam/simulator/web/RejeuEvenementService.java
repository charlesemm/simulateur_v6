package ci.cnam.simulator.web;

import ci.cnam.simulator.security.AuditService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Rejeu d'un evenement echoue : un evenement dont le statut n'est pas {@code ECRIT} est remis a {@code ECRIT}.
 * Le contenu de l'evenement n'est jamais modifie. Un evenement deja ecrit n'est pas rejoue (aucun doublon possible).
 */
@Service
public class RejeuEvenementService {

    /** Statut d'un evenement ecrit normalement par le DatabaseSink. */
    public static final String ECRIT = "ECRIT";

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public RejeuEvenementService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** @throws IllegalArgumentException evenement inconnu ou deja ecrit */
    public void rejouer(UUID eventId, String acteur) {
        int modifies = jdbc.update("update evenement set statut = ? where event_id = ? and statut <> ?",
                ECRIT, eventId, ECRIT);
        if (modifies == 0) {
            boolean existe = Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from evenement where event_id = ?)", Boolean.class, eventId));
            throw new IllegalArgumentException(existe
                    ? "Cet evenement est deja ecrit : rien a rejouer"
                    : "Evenement inconnu : " + eventId);
        }
        audit.enregistrer(acteur, "EVENEMENT_REJOUE", eventId.toString(), null);
    }
}

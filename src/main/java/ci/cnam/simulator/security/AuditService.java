package ci.cnam.simulator.security;

import ci.cnam.simulator.persistence.entity.AuditLog;
import ci.cnam.simulator.persistence.repository.AuditLogRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Journal d'audit : qui a fait quoi, sur quoi, quand. L'ecriture ne fait jamais echouer l'action auditee (la base
 * peut etre coupee volontairement par un test de chaos, et "tout retablir" doit passer quand meme) : en cas d'echec,
 * la ligne reste dans les journaux de l'application. Ne jamais passer de mot de passe ni de cle dans {@code details}.
 */
@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository journal;
    private final Clock horloge;

    public AuditService(AuditLogRepository journal, Clock horloge) {
        this.journal = journal;
        this.horloge = horloge;
    }

    public void enregistrer(String acteur, String action, String cible, String details) {
        LOG.info("{} {} {} {}", acteur, action, cible, details == null ? "" : details);
        try {
            journal.save(new AuditLog(horloge.instant(), acteur, action, cible, details));
        } catch (RuntimeException e) {
            LOG.warn("Audit non ecrit en base ({} {} {}) : {}", acteur, action, cible, e.getMessage());
        }
    }

    public List<AuditLog> derniers() {
        return journal.findTop100ByOrderByDateDesc();
    }
}

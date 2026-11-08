package ci.cnam.simulator.quality.planification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La seule chose qui tourne en tache de fond pour les analyses : un passage a la minute qui fait
 * DEUX choses, et deux seulement.
 *
 * <ol>
 *   <li>ouvrir les echeances dues depuis le dernier passage ;</li>
 *   <li>passer en SILENCE les echeances retirees dont le delai de rapport est depasse.</li>
 * </ol>
 *
 * <p>Il ne declenche AUCUNE analyse et n'appelle jamais l'outil : il ouvre une echeance, le script
 * exterieur vient la lire. Il ne genere aucune simulation non plus.
 *
 * <p>{@code fixedDelay} et non un {@code cron} : le travail est « ce qui est du depuis la derniere
 * fois », calcule par le service a partir de son horloge, et non « il est 3 h pile ». Un passage
 * manque (application arretee, machine chargee) est donc rattrape au passage suivant au lieu
 * d'etre perdu, et rien ne depend du reglage d'un cron sur l'heure locale.
 *
 * <p>L'operation est IDEMPOTENTE : deux passages dans la meme minute n'ouvrent qu'une echeance
 * (unicite {@code (planification_id, date_echeance)} en base).
 *
 * <p>LIMITE ASSUMEE : il n'y a pas de verrou distribue. Une seule instance d'Echo doit tourner.
 * Le jour ou deux instances tourneraient, chacune passerait a la minute : l'unicite en base les
 * empecherait d'ouvrir deux fois la MEME echeance, mais rien ne garantirait l'ordre des reports de
 * statut, et il faudrait alors un verrou (table d'election, ShedLock, ou Quartz en cluster). Pas de
 * Quartz ici : Spring suffit a une instance.
 */
@Component
public class OuvertureEcheances {

    private static final Logger LOG = LoggerFactory.getLogger(OuvertureEcheances.class);

    /** A la minute : c'est la granularite de l'heure saisie. */
    private static final long PERIODE_MS = 60_000L;

    private final PlanificationService planification;

    public OuvertureEcheances(PlanificationService planification) {
        this.planification = planification;
    }

    @Scheduled(fixedDelay = PERIODE_MS, initialDelay = PERIODE_MS)
    public void passage() {
        try {
            int ouvertes = planification.ouvrirEcheancesDues();
            int silences = planification.marquerSilences();
            if (ouvertes > 0 || silences > 0) {
                LOG.info("Planification : {} echeance(s) ouverte(s), {} silence(s) constate(s)",
                        ouvertes, silences);
            }
        } catch (RuntimeException e) {
            // La base peut etre coupee volontairement par un test de chaos : le passage suivant
            // rattrapera. Une tache planifiee qui leve une exception ne doit pas arreter le calendrier.
            LOG.warn("Passage de la planification interrompu : {}", e.getMessage());
        }
    }
}

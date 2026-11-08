package ci.cnam.simulator.quality.planification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les taches planifiees de Spring. C'est tout ce qu'il faut : PAS de Quartz, pas de
 * planificateur externe, aucune dependance de plus.
 *
 * <p>Une seule tache existe ({@link OuvertureEcheances}), et elle ne declenche aucune analyse : elle
 * ouvre des echeances que le script exterieur vient lire.
 */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}

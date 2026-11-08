package ci.cnam.simulator.quality;

import ci.cnam.simulator.SimulateurProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal des echanges avec l'outil d'analyse, et lecture de l'etat qui en decoule.
 *
 * <p>Echo n'appelle jamais l'outil : il expose, le script prend. Un ENVOI est donc inscrit quand le
 * script retire l'export, une RECEPTION quand il pousse un rapport, accepte ou refuse.
 *
 * <p>Le silence du script n'est pas ecrit : il se DEDUIT du journal et de l'heure (export retire,
 * aucun rapport, delai depasse). Rien n'a donc besoin de tourner en tache de fond ici — la
 * planification et son {@code @Scheduled} arrivent au prompt 15, et ce calcul reste vrai sans eux.
 */
@Service
public class EchangeService {

    private static final Logger LOG = LoggerFactory.getLogger(EchangeService.class);

    /** Au-dela, le detail est tronque : la colonne fait 500 caracteres. */
    static final int DETAIL_MAX = 500;

    private final EchangeStore store;
    private final Clock horloge;
    private final Duration delaiRapport;
    private final Optional<SuiviEcheances> suivi;

    public EchangeService(EchangeStore store, Clock horloge, SimulateurProperties proprietes,
                          Optional<SuiviEcheances> suivi) {
        this.store = store;
        this.horloge = horloge;
        this.delaiRapport = Duration.ofMinutes(proprietes.qualite().delaiRapportMinutes());
        this.suivi = suivi;
    }

    /** Delai au-dela duquel un export retire sans rapport devient un SILENCE. */
    public Duration delaiRapport() {
        return delaiRapport;
    }

    /**
     * Le script a retire l'export : c'est l'ENVOI. Inscrit apres que les octets sont partis, pour
     * qu'un telechargement interrompu ne compte pas comme un envoi reussi.
     */
    public void tracerRetrait(UUID runId, String detail) {
        tracer(runId, SensEchange.ENVOI, null, StatutEchange.EXPORT_RETIRE, detail);
        reporter(runId, s -> s.retraitConstate(runId, detail));
    }

    /** Le script a pousse un rapport exploitable. */
    public void tracerRapport(UUID runId, String versionOutil, String detail) {
        tracer(runId, SensEchange.RECEPTION, versionOutil, StatutEchange.RAPPORT_RECU, detail);
        reporter(runId, s -> s.rapportConstate(runId, detail));
    }

    /**
     * Le script a pousse un rapport accepte, mais aucune detection n'est exploitable : aucune mesure
     * fiable n'est possible (cahier 2.3 et 4.3), c'est une information sur l'outil.
     */
    public void tracerRapportSansDetail(UUID runId, String versionOutil, String detail) {
        tracer(runId, SensEchange.RECEPTION, versionOutil, StatutEchange.RAPPORT_SANS_DETAIL, detail);
        reporter(runId, s -> s.refusConstate(runId, detail));
    }

    /**
     * L'envoi a ete refuse en bloc. Inscrit dans une transaction PROPRE : l'envoi refuse a fait
     * annuler la sienne, et la trace doit survivre a cette annulation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tracerRapportMalforme(UUID runId, String versionOutil, String detail) {
        tracer(runId, SensEchange.RECEPTION, versionOutil, StatutEchange.RAPPORT_MALFORME, detail);
        reporter(runId, s -> s.refusConstate(runId, detail));
    }

    /**
     * Reporte le meme fait sur le calendrier des analyses, s'il y en a un (prompt 15). Le journal
     * des echanges reste la trace de reference : un report qui echoue ne doit pas faire echouer
     * l'appel du script, exactement comme la trace elle-meme.
     */
    private void reporter(UUID runId, java.util.function.Consumer<SuiviEcheances> report) {
        suivi.ifPresent(s -> {
            try {
                report.accept(s);
            } catch (RuntimeException e) {
                LOG.warn("Echeance non mise a jour pour le run {} : {}", runId, e.getMessage());
            }
        });
    }

    /** Le journal complet d'un run, du plus ancien au plus recent. */
    public List<Echange> journal(UUID runId) {
        return store.journal(runId);
    }

    /**
     * Ou en est l'echange, ou vide si aucun echange n'a eu lieu pour ce run (une simulation jamais
     * retiree et jamais analysee n'est pas un echec : il n'y a rien a dire).
     *
     * <p>Regle, dans cet ordre :
     * <ol>
     *   <li>un rapport exploitable est arrive : mesurable, c'est le seul cas qui rend des chiffres ;</li>
     *   <li>le dernier rapport a ete refuse ou etait sans detail : cet echec-la ;</li>
     *   <li>un export a ete retire sans rapport : SILENCE passe le delai, sinon ATTENTE_RAPPORT.</li>
     * </ol>
     */
    public Optional<EtatEchange> etat(UUID runId) {
        List<Echange> journal = store.journal(runId);
        if (journal.isEmpty()) {
            return Optional.empty();
        }
        Echange recu = dernier(journal, e -> e.statut() == StatutEchange.RAPPORT_RECU);
        if (recu != null) {
            return Optional.of(new EtatEchange(StatutEchange.RAPPORT_RECU, recu.date(), recu.detail()));
        }
        Echange reception = dernier(journal, e -> e.sens() == SensEchange.RECEPTION);
        if (reception != null) {
            return Optional.of(new EtatEchange(reception.statut(), reception.date(), reception.detail()));
        }
        Echange envoi = dernier(journal, e -> e.sens() == SensEchange.ENVOI);
        if (envoi == null) {
            return Optional.empty();
        }
        Instant limite = envoi.date().plus(delaiRapport);
        boolean depasse = !horloge.instant().isBefore(limite);
        return Optional.of(new EtatEchange(
                depasse ? StatutEchange.SILENCE : StatutEchange.ATTENTE_RAPPORT,
                envoi.date(),
                depasse
                        ? "Le jeu a ete retire et aucun rapport n'est arrive dans les "
                                + delaiRapport.toMinutes() + " minutes : aucune mesure n'est possible."
                        : "Le jeu a ete retire, le rapport du script est attendu avant "
                                + limite + " : aucun chiffre tant qu'il n'est pas arrive."));
    }

    private static Echange dernier(List<Echange> journal, java.util.function.Predicate<Echange> filtre) {
        Echange trouve = null;
        for (Echange e : journal) {
            if (filtre.test(e)) {
                trouve = e;
            }
        }
        return trouve;
    }

    private void tracer(UUID runId, SensEchange sens, String versionOutil, StatutEchange statut, String detail) {
        if (!statut.stockable()) {
            throw new IllegalArgumentException("Statut deduit, jamais inscrit : " + statut);
        }
        Echange echange = new Echange(UUID.randomUUID(), runId, sens, horloge.instant(),
                tronquer(versionOutil, 80), statut, tronquer(detail, DETAIL_MAX));
        try {
            store.tracer(echange);
        } catch (RuntimeException e) {
            // Un run inconnu (cle etrangere) ou une base indisponible ne doit pas faire echouer
            // l'appel du script : la trace est une preuve, pas une condition de service.
            LOG.warn("Echange non trace pour le run {} ({} {})", runId, sens, statut, e);
        }
    }

    private static String tronquer(String valeur, int max) {
        if (valeur == null) {
            return null;
        }
        String propre = valeur.strip();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }
}

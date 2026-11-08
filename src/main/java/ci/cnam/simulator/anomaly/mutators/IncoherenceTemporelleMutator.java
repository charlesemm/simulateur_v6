package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Une date de soin qui ne tient pas devant les autres dates de la simulation : avant l'ouverture des droits
 * (antidatee), apres leur fermeture, ou loin dans le futur. La date reste une date valide et bien formatee :
 * elle n'est fausse que RAPPORTEE A LA PERIODE DE DROITS, qui est dans le meme evenement.
 *
 * <p>Seule la date de soin bouge. La periode de droits, elle, reste propre : c'est
 * {@link IncoherenceCroiseeMutator} qui s'en occupe, et par un autre mecanisme.
 */
@Component
public class IncoherenceTemporelleMutator implements MutatorRelation {

    /** Trois facons, tirees a parts egales : antidatee, apres la fermeture, dans le futur. */
    private static final int FACONS = 3;

    @Override
    public AnomalyType type() {
        return AnomalyType.INCOHERENCE_TEMPORELLE;
    }

    @Override
    public List<String> champsRequis() {
        return List.of("dateSoin", "dateDebutDroits", "dateFinDroits");
    }

    @Override
    public Optional<MutationRelation> muter(Map<String, Object> payload, SeededRandom random) {
        LocalDate soin = date(payload.get("dateSoin"));
        LocalDate debut = date(payload.get("dateDebutDroits"));
        LocalDate fin = date(payload.get("dateFinDroits"));
        if (soin == null || debut == null || fin == null) {
            return Optional.empty();
        }
        LocalDate fausse = switch (random.entre(0, FACONS - 1)) {
            case 0 -> debut.minusDays(random.entre(1, 400));
            case 1 -> fin.plusDays(random.entre(1, 400));
            default -> soin.plusDays(random.entre(30, 400));
        };
        return fausse.equals(soin) ? Optional.empty()
                : Optional.of(new MutationRelation("dateSoin", fausse.toString()));
    }

    /** Accepte une date, ou une date ecrite en texte ISO (c'est ainsi que le payload la porte). */
    static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate date) {
            return date;
        }
        if (valeur instanceof String texte) {
            try {
                return LocalDate.parse(texte);
            } catch (RuntimeException pasUneDate) {
                return null;
            }
        }
        return null;
    }
}

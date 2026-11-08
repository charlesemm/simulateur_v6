package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Des droits declares actifs alors que la periode de droits est CLOSE a la date du soin. « Droits ouverts » est
 * une valeur valide ; une date de fin passee est une date valide ; c'est leur CONJONCTION qui est fausse. Aucun
 * controle de champ ne la voit, seule une regle qui lit les deux ensemble.
 *
 * <p>C'est la date de fin qui recule, pas le drapeau : un seul champ bouge, donc une seule ligne au corrige.
 * uq_anomaly_injected (event_id, type, champ) n'accepterait pas deux lignes du meme type sur le meme
 * evenement, une par champ de la contradiction.
 */
@Component
public class IncoherenceCroiseeMutator implements MutatorRelation {

    @Override
    public AnomalyType type() {
        return AnomalyType.INCOHERENCE_CROISEE;
    }

    @Override
    public List<String> champsRequis() {
        return List.of("droitsOuverts", "dateSoin", "dateFinDroits");
    }

    @Override
    public Optional<MutationRelation> muter(Map<String, Object> payload, SeededRandom random) {
        if (!actifs(payload.get("droitsOuverts"))) {
            return Optional.empty();
        }
        LocalDate soin = IncoherenceTemporelleMutator.date(payload.get("dateSoin"));
        LocalDate debut = IncoherenceTemporelleMutator.date(payload.get("dateDebutDroits"));
        LocalDate fin = IncoherenceTemporelleMutator.date(payload.get("dateFinDroits"));
        if (soin == null || fin == null) {
            return Optional.empty();
        }
        // La periode se ferme avant le soin ; elle reste ouverte apres son debut quand on le connait, pour que
        // la seule chose fausse soit la contradiction avec des droits actifs.
        LocalDate fermeture = soin.minusDays(random.entre(1, 180));
        if (debut != null && fermeture.isBefore(debut)) {
            fermeture = soin.minusDays(1);
            if (fermeture.isBefore(debut)) {
                return Optional.empty();
            }
        }
        return fermeture.equals(fin) ? Optional.empty()
                : Optional.of(new MutationRelation("dateFinDroits", fermeture.toString()));
    }

    private static boolean actifs(Object valeur) {
        return valeur instanceof Boolean actifs ? actifs : "true".equals(String.valueOf(valeur));
    }
}

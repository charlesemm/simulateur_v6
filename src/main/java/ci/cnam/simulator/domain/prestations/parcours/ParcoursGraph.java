package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Ordre des etapes et ramification apres la consultation (examens, hospitalisation ou direct). */
@Component
public class ParcoursGraph {

    private final ParcoursProperties proprietes;

    public ParcoursGraph(ParcoursProperties proprietes) {
        this.proprietes = proprietes;
    }

    /** Probabilites par defaut (configuration de l application). */
    public ParcoursProperties proprietes() {
        return proprietes;
    }

    /** Tire la voie selon les probabilites par defaut (un seul tirage du generateur). */
    public VoieParcours tirerVoie(SeededRandom random) {
        return tirerVoie(random, proprietes);
    }

    /** Tire la voie selon des probabilites donnees (ex. celles d un scenario). */
    public VoieParcours tirerVoie(SeededRandom random, ParcoursProperties probas) {
        double tirage = random.nextDouble();
        if (tirage < probas.probaExamens()) {
            return VoieParcours.EXAMENS;
        }
        if (tirage < probas.probaExamens() + probas.probaHospitalisation()) {
            return VoieParcours.HOSPITALISATION;
        }
        return VoieParcours.DIRECT;
    }

    /** Etape suivante, ou vide si le parcours est termine (finalisation, ou acces refuse). */
    public Optional<ParcoursEtape> suivante(ParcoursEtape courante, ParcoursContext contexte) {
        return switch (courante) {
            case VERIFICATION_DROITS -> contexte.accesRefuse()
                    ? Optional.empty() : Optional.of(ParcoursEtape.OUVERTURE_FACTURE);
            case OUVERTURE_FACTURE -> Optional.of(ParcoursEtape.CONSULTATION);
            case CONSULTATION -> Optional.of(switch (contexte.voie()) {
                case EXAMENS -> ParcoursEtape.EXAMENS;
                case HOSPITALISATION -> ParcoursEtape.HOSPITALISATION;
                case DIRECT -> ParcoursEtape.PRESCRIPTION;
            });
            case EXAMENS, HOSPITALISATION -> Optional.of(ParcoursEtape.PRESCRIPTION);
            case PRESCRIPTION -> Optional.of(ParcoursEtape.PHARMACIE);
            case PHARMACIE -> Optional.of(ParcoursEtape.FINALISATION);
            case FINALISATION -> Optional.empty();
        };
    }
}

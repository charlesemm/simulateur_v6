package ci.cnam.simulator.export;

import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.core.engine.RunInfo;
import java.util.Map;

/**
 * Taux d'anomalies d'une simulation terminee, par domaine. L'export en a besoin pour doser les deux
 * anomalies de fichier ; il n'a pas a savoir d'ou ils viennent (ici du scenario relu en base).
 */
public interface ProfilsAnomalies {

    /** Profil par nom de domaine ; carte vide si le scenario est introuvable ou illisible. */
    Map<String, AnomalyProfile> parDomaine(RunInfo run);
}

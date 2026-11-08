package ci.cnam.simulator.quality;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Ce qu'Echo attend qu'on analyse maintenant.
 *
 * <p>La source de verite ({@link AnalysesAttendues}) est la planification du prompt 15. Elle n'existe
 * pas encore : le service rend alors une liste vide, ce qui est une reponse juste (« rien a analyser
 * pour l'instant ») et non une erreur. Le jour ou la planification est ecrite, il suffit qu'elle
 * publie un bean : ni cet endpoint ni le script d'analyse ne changent.
 */
@Service
public class AnalyseService {

    private final ObjectProvider<AnalysesAttendues> source;

    public AnalyseService(ObjectProvider<AnalysesAttendues> source) {
        this.source = source;
    }

    public List<AnalyseAttendue> attendues() {
        AnalysesAttendues planification = source.getIfAvailable();
        return planification == null ? List.of() : List.copyOf(planification.enAttente());
    }

    /** Vrai tant que la planification n'est pas branchee : l'endpoint le dit, il ne le cache pas. */
    public boolean planificationAbsente() {
        return source.getIfAvailable() == null;
    }
}

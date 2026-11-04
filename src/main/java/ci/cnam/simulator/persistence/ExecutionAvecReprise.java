package ci.cnam.simulator.persistence;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.core.engine.ExecutionAtomique;
import org.springframework.stereotype.Component;

/**
 * Un parcours ne touche plus la base : ses factures sont tenues en memoire puis ecrites par le sink dans la
 * transaction du lot d'evenements, donc tout ou rien. Ne reste que les rares acces de lecture (blocs de numeros de
 * facture) : s'ils echouent parce que la base est injoignable, le parcours est rejoue a l'identique, sans transaction.
 */
@Component
public class ExecutionAvecReprise implements ExecutionAtomique {

    private final long patienceMs;

    public ExecutionAvecReprise(SimulateurProperties proprietes) {
        this.patienceMs = proprietes.ecriture().indisponibiliteMaxMs();
    }

    @Override
    public void executer(Runnable travail) {
        travail.run();
    }

    @Override
    public boolean estIndisponibilite(Throwable erreur) {
        return IndisponibiliteBase.estCausee(erreur);
    }

    @Override
    public long patienceMs() {
        return patienceMs;
    }
}

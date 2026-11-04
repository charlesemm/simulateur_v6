package ci.cnam.simulator.persistence;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.core.engine.ExecutionAtomique;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Un parcours dans UNE transaction : les etapes (facture, lignes, historique) s'y rattachent et sont validees
 * ensemble, ou annulees ensemble. C'est ce qui garantit qu'une coupure ne laisse aucune facture a moitie ecrite.
 */
@Component
public class ExecutionTransactionnelle implements ExecutionAtomique {

    private final TransactionTemplate transaction;
    private final long patienceMs;

    public ExecutionTransactionnelle(PlatformTransactionManager tx, SimulateurProperties proprietes) {
        this.transaction = new TransactionTemplate(tx);
        this.patienceMs = proprietes.ecriture().indisponibiliteMaxMs();
    }

    @Override
    public void executer(Runnable travail) {
        transaction.executeWithoutResult(status -> travail.run());
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

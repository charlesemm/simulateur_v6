package ci.cnam.simulator.domain.prestations.tarification;

import ci.cnam.simulator.domain.prestations.facture.NouvelleLigne;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Calcul des parts d'une ligne. BigDecimal uniquement, echelle 2, arrondi HALF_UP.
 * <ul>
 *   <li>part CNAM = montant x taux CNAM du type de prestation, arrondie ;</li>
 *   <li>ticket moderateur = montant - part CNAM ;</li>
 *   <li>part complementaire = ticket moderateur x taux complementaire, arrondie ;</li>
 *   <li>part assure = le reste : le reliquat d'arrondi lui revient, l'invariant des trois parts est donc exact.</li>
 * </ul>
 * Hypothese a valider : tous les assures ont une assurance complementaire (elle n'est pas encore modelisee).
 */
@Service
public class TarificationService {

    private static final int ECHELLE = 2;
    private static final RoundingMode ARRONDI = RoundingMode.HALF_UP;

    private final TauxRepository taux;
    private volatile RegleTaux regle;

    @Autowired
    public TarificationService(TauxRepository taux) {
        this.taux = taux;
    }

    /** Regle fixee a la construction (tests unitaires, sans base). */
    public TarificationService(RegleTaux regle) {
        this.taux = null;
        this.regle = regle;
    }

    /** Relit la table taux : une modification des taux est prise en compte sans redemarrer. */
    public void recharger() {
        if (taux != null) {
            regle = taux.charger();
        }
    }

    public RegleTaux regle() {
        RegleTaux courante = regle;
        if (courante == null) {
            courante = taux.charger();
            regle = courante;
        }
        return courante;
    }

    /** Repartit un montant deja connu. */
    public Repartition repartir(BigDecimal montant, TypePrestation type) {
        if (montant == null || montant.signum() < 0) {
            throw new IllegalArgumentException("Montant negatif ou absent : " + montant);
        }
        RegleTaux r = regle();
        BigDecimal total = montant.setScale(ECHELLE, ARRONDI);
        BigDecimal cnam = total.multiply(r.tauxCnam(type)).setScale(ECHELLE, ARRONDI);
        BigDecimal ticket = total.subtract(cnam);
        BigDecimal complementaire = ticket.multiply(r.tauxComplementaire()).setScale(ECHELLE, ARRONDI);
        BigDecimal assure = ticket.subtract(complementaire);
        return new Repartition(total, cnam, complementaire, assure);
    }

    /** Montant d'une ligne : tarif unitaire du catalogue x quantite, puis repartition. */
    public Repartition repartir(BigDecimal tarifUnitaire, int quantite, TypePrestation type) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("Quantite invalide : " + quantite);
        }
        return repartir(tarifUnitaire.multiply(BigDecimal.valueOf(quantite)), type);
    }

    /** Construit la ligne de facture prete a etre ajoutee, avec ses trois parts. */
    public NouvelleLigne ligne(String code, String designation, LocalDateTime dateSoin, int quantite,
                               BigDecimal tarifUnitaire, TypePrestation type) {
        Repartition p = repartir(tarifUnitaire, quantite, type);
        return new NouvelleLigne(code, designation, dateSoin, quantite, tarifUnitaire.setScale(ECHELLE, ARRONDI),
                p.montant(), p.partCnam(), p.partAssuranceComplementaire(), p.partAssure());
    }
}

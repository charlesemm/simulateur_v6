package ci.cnam.simulator.domain.prestations.etapes;

import ci.cnam.simulator.domain.prestations.facture.NouvelleLigne;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ligne a montants FICTIFS en francs CFA, valable jusqu'a la brique 5 (tarification) : les parts unitaires sont
 * des constantes choisies par l'etape, aucun taux n'est calcule ici.
 */
final class LigneFictive {

    private LigneFictive() {
    }

    static NouvelleLigne de(String code, String designation, LocalDateTime date, int quantite,
                            String prixUnitaire, String partCnam, String partComplementaire, String partAssure) {
        BigDecimal q = BigDecimal.valueOf(quantite);
        BigDecimal prix = new BigDecimal(prixUnitaire);
        return new NouvelleLigne(code, designation, date, quantite, prix, prix.multiply(q),
                new BigDecimal(partCnam).multiply(q),
                new BigDecimal(partComplementaire).multiply(q),
                new BigDecimal(partAssure).multiply(q));
    }
}

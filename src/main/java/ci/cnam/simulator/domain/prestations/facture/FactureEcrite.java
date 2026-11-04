package ci.cnam.simulator.domain.prestations.facture;

import ci.cnam.simulator.sink.EcritureAnnexe;
import java.util.List;

/**
 * Une facture terminee avec ses lignes et son historique, prete a etre ecrite d'un bloc : elle est validee dans la
 * transaction du lot d'evenements, donc jamais a moitie. L'horodatage reel de l'historique est fixe a l'ecriture.
 */
public record FactureEcrite(Facture facture, List<FactureLigne> lignes, List<FactureHistorique> historique)
        implements EcritureAnnexe {

    public FactureEcrite {
        lignes = List.copyOf(lignes);
        historique = List.copyOf(historique);
    }
}

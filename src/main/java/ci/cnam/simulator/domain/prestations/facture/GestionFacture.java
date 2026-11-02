package ci.cnam.simulator.domain.prestations.facture;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operations sur la facture dont les etapes du parcours ont besoin. {@link FactureService} l'implemente
 * (base de donnees) ; les tests de parcours en fournissent une version en memoire.
 */
public interface GestionFacture {

    Facture ouvrir(NouvelleFacture demande);

    Facture refuser(NouvelleFacture demande);

    FactureLigne ajouterLigne(UUID factureId, NouvelleLigne ligne);

    Facture changerStatut(UUID factureId, FactureStatut nouveau, LocalDateTime dateSimulee);
}

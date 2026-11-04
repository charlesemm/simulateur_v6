package ci.cnam.simulator.domain.prestations.facture;

import ci.cnam.simulator.sink.EcritureAnnexe;
import java.time.LocalDateTime;
import java.util.Optional;
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

    /**
     * Termine le suivi d'une facture (reussi ou non) et rend, s'il y a lieu, ce qu'il reste a ecrire en base. Une
     * implementation qui ecrit tout de suite (FactureService) ou qui n'ecrit rien (tests) rend {@code Optional.empty()}.
     */
    default Optional<EcritureAnnexe> clore(UUID factureId) {
        return Optional.empty();
    }
}

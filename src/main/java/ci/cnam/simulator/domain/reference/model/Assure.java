package ci.cnam.simulator.domain.reference.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Assure principal. {@code regime} est l'un des deux regimes de la couverture maladie universelle,
 * code comme dans {@code TB_TV_REGIMES}. Il est porte par l'assure et non par la facture : un
 * assure ne change pas de regime au gre de ses soins.
 */
public record Assure(
        UUID id,
        String numeroAssure,
        String nom,
        String prenoms,
        LocalDate dateNaissance,
        Genre genre,
        Regime regime,
        StatutCotisation statutCotisation,
        LocalDate dateDebutDroits,
        LocalDate dateFinDroits) {
}

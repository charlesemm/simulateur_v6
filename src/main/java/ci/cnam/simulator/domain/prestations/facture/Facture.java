package ci.cnam.simulator.domain.prestations.facture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Facture (feuille de soins). Dates en temps simule. Le changement de statut passe par {@link #avecStatut}. */
public record Facture(
        UUID id,
        UUID runId,
        String numeroFacture,
        UUID assureId,
        UUID beneficiaireId,
        UUID centreSanteId,
        TypeBon typeBon,
        FactureStatut statut,
        LocalDateTime dateOuverture,
        LocalDateTime dateFinalisation,
        BigDecimal montantTotal,
        BigDecimal partCnam,
        BigDecimal partAssuranceComplementaire,
        BigDecimal partAssure) {

    /** Ajoute une ligne aux totaux (simples sommes des lignes). */
    public Facture avecLigne(BigDecimal montant, BigDecimal cnam, BigDecimal complementaire, BigDecimal assure) {
        return new Facture(id, runId, numeroFacture, assureId, beneficiaireId, centreSanteId, typeBon, statut,
                dateOuverture, dateFinalisation, montantTotal.add(montant), partCnam.add(cnam),
                partAssuranceComplementaire.add(complementaire), partAssure.add(assure));
    }

    public Facture avecStatut(FactureStatut nouveau, LocalDateTime dateSimulee) {
        statut.verifierTransitionVers(nouveau);
        LocalDateTime finalisation = nouveau == FactureStatut.FINALISEE ? dateSimulee : dateFinalisation;
        return new Facture(id, runId, numeroFacture, assureId, beneficiaireId, centreSanteId, typeBon, nouveau,
                dateOuverture, finalisation, montantTotal, partCnam, partAssuranceComplementaire, partAssure);
    }
}

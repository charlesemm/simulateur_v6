package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureLigne;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.facture.NouvelleFacture;
import ci.cnam.simulator.domain.prestations.facture.NouvelleLigne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Factures en memoire pour les tests de parcours : meme machine a etats, identifiants pilotes par une graine. */
class FactureEnMemoire implements GestionFacture {

    private final SeededRandom random;
    private final Map<UUID, Facture> factures = new HashMap<>();
    private final Map<UUID, List<FactureLigne>> lignes = new HashMap<>();
    private int sequence;

    FactureEnMemoire(long graine) {
        this.random = new SeededRandom(graine);
    }

    @Override
    public Facture ouvrir(NouvelleFacture d) {
        return creer(d, FactureStatut.OUVERTE);
    }

    @Override
    public Facture refuser(NouvelleFacture d) {
        return creer(d, FactureStatut.REFUSEE);
    }

    private Facture creer(NouvelleFacture d, FactureStatut statut) {
        UUID id = random.nextUuid();
        Facture f = new Facture(id, d.runId(), "FAC-%04d-%06d".formatted(d.dateOuverture().getYear(), ++sequence),
                d.assureId(), d.beneficiaireId(), d.centreSanteId(), d.typeBon(), statut, d.dateOuverture(), null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        factures.put(id, f);
        lignes.put(id, new ArrayList<>());
        return f;
    }

    @Override
    public FactureLigne ajouterLigne(UUID factureId, NouvelleLigne l) {
        Facture f = factures.get(factureId);
        if (f.statut().estTerminal()) {
            throw new IllegalStateException("Facture terminale : " + f.statut());
        }
        List<FactureLigne> liste = lignes.get(factureId);
        FactureLigne ligne = new FactureLigne(random.nextUuid(), f.runId(), factureId, liste.size() + 1, l.code(),
                l.designation(), l.dateSoin(), l.quantite(), l.prixUnitaire(), l.montant(), l.partCnam(),
                l.partAssuranceComplementaire(), l.partAssure());
        liste.add(ligne);
        factures.put(factureId, new Facture(f.id(), f.runId(), f.numeroFacture(), f.assureId(), f.beneficiaireId(),
                f.centreSanteId(), f.typeBon(), f.statut(), f.dateOuverture(), f.dateFinalisation(),
                f.montantTotal().add(l.montant()), f.partCnam().add(l.partCnam()),
                f.partAssuranceComplementaire().add(l.partAssuranceComplementaire()),
                f.partAssure().add(l.partAssure())));
        return ligne;
    }

    @Override
    public Facture changerStatut(UUID factureId, FactureStatut nouveau, LocalDateTime dateSimulee) {
        Facture modifiee = factures.get(factureId).avecStatut(nouveau, dateSimulee);
        factures.put(factureId, modifiee);
        return modifiee;
    }

    List<FactureLigne> lignes(UUID factureId) {
        return lignes.get(factureId);
    }
}

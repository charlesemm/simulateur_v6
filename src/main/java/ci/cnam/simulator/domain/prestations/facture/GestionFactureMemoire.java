package ci.cnam.simulator.domain.prestations.facture;

import ci.cnam.simulator.sink.EcritureAnnexe;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Factures d'un parcours tenues EN MEMOIRE : aucune requete pendant le parcours. A la fin, {@link #clore} rend la
 * facture complete ({@link FactureEcrite}), que le sink ecrit par lots dans la transaction des evenements. Les
 * regles sont les memes que {@link FactureService} (machine a etats, totaux, identifiants derives).
 */
@Component
@Primary
public class GestionFactureMemoire implements GestionFacture {

    private static final class Dossier {
        Facture facture;
        final List<FactureLigne> lignes = new ArrayList<>();
        final List<FactureHistorique> historique = new ArrayList<>();

        Dossier(Facture facture) {
            this.facture = facture;
        }
    }

    private final NumerotationFactures numerotation;
    private final Map<UUID, Dossier> dossiers = new ConcurrentHashMap<>();

    public GestionFactureMemoire(NumerotationFactures numerotation) {
        this.numerotation = numerotation;
    }

    @Override
    public Facture ouvrir(NouvelleFacture demande) {
        return creer(demande, FactureStatut.OUVERTE);
    }

    @Override
    public Facture refuser(NouvelleFacture demande) {
        return creer(demande, FactureStatut.REFUSEE);
    }

    private Facture creer(NouvelleFacture d, FactureStatut statut) {
        String numero = "FAC-%04d-%06d".formatted(d.dateOuverture().getYear(), numerotation.suivant());
        UUID id = IdentifiantsFacture.de("facture", d.runId(), numero);
        Facture facture = new Facture(id, d.runId(), numero, d.assureId(), d.beneficiaireId(), d.centreSanteId(),
                d.typeBon(), statut, d.dateOuverture(), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
        Dossier dossier = new Dossier(facture);
        historiser(dossier, null, statut, d.dateOuverture());
        dossiers.put(id, dossier);
        return facture;
    }

    @Override
    public FactureLigne ajouterLigne(UUID factureId, NouvelleLigne l) {
        Dossier dossier = dossier(factureId);
        Facture facture = dossier.facture;
        if (facture.statut().estTerminal()) {
            throw new IllegalStateException("Facture " + facture.numeroFacture() + " au statut terminal "
                    + facture.statut() + " : ligne refusee");
        }
        int numero = dossier.lignes.size() + 1;
        FactureLigne ligne = new FactureLigne(IdentifiantsFacture.de("ligne", factureId, String.valueOf(numero)),
                facture.runId(), factureId, numero, l.code(), l.designation(), l.dateSoin(), l.quantite(),
                l.prixUnitaire(), l.montant(), l.partCnam(), l.partAssuranceComplementaire(), l.partAssure());
        dossier.lignes.add(ligne);
        dossier.facture = facture.avecLigne(l.montant(), l.partCnam(), l.partAssuranceComplementaire(),
                l.partAssure());
        return ligne;
    }

    @Override
    public Facture changerStatut(UUID factureId, FactureStatut nouveau, LocalDateTime dateSimulee) {
        Dossier dossier = dossier(factureId);
        FactureStatut ancien = dossier.facture.statut();
        dossier.facture = dossier.facture.avecStatut(nouveau, dateSimulee);   // refuse une transition interdite
        historiser(dossier, ancien, nouveau, dateSimulee);
        return dossier.facture;
    }

    @Override
    public Optional<EcritureAnnexe> clore(UUID factureId) {
        Dossier dossier = dossiers.remove(factureId);
        if (dossier == null) {
            return Optional.empty();
        }
        return Optional.of(new FactureEcrite(dossier.facture, dossier.lignes, dossier.historique));
    }

    private Dossier dossier(UUID factureId) {
        Dossier dossier = dossiers.get(factureId);
        if (dossier == null) {
            throw new IllegalArgumentException("Facture inconnue : " + factureId);
        }
        return dossier;
    }

    private static void historiser(Dossier dossier, FactureStatut ancien, FactureStatut nouveau,
                                   LocalDateTime dateSimulee) {
        Facture f = dossier.facture;
        int rang = dossier.historique.size() + 1;
        dossier.historique.add(new FactureHistorique(
                IdentifiantsFacture.de("historique", f.id(), String.valueOf(rang)), f.runId(), f.id(), ancien,
                nouveau, dateSimulee, null));
    }
}

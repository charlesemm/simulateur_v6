package ci.cnam.simulator.domain.prestations.facture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.sink.EcritureAnnexe;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Les factures tenues en memoire suivent les memes regles que FactureService, sans toucher la base. */
class GestionFactureMemoireTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 1, 8, 0);
    private static final UUID RUN = new UUID(1, 1);

    /** Numerotation sans base : 1, 2, 3... */
    private static NumerotationFactures numerotationSansBase(long depart) {
        AtomicLong compteur = new AtomicLong(depart);
        return new NumerotationFactures(null) {
            @Override
            public long suivant() {
                return compteur.getAndIncrement();
            }
        };
    }

    private static GestionFactureMemoire gestion() {
        return new GestionFactureMemoire(numerotationSansBase(1));
    }

    private static NouvelleFacture demande() {
        UUID assure = new UUID(2, 2);
        return new NouvelleFacture(RUN, assure, assure, new UUID(3, 3), TypeBon.AMBULATOIRE, T0);
    }

    private static NouvelleLigne ligne(String montant, String cnam, String complementaire, String assure) {
        return new NouvelleLigne("C01", "Consultation", T0.plusMinutes(10), 1, new BigDecimal(montant),
                new BigDecimal(montant), new BigDecimal(cnam), new BigDecimal(complementaire),
                new BigDecimal(assure));
    }

    @Test
    void unParcoursCompletDonneUneFactureCoherenteEtSonHistorique() {
        GestionFactureMemoire gestion = gestion();
        Facture ouverte = gestion.ouvrir(demande());
        assertThat(ouverte.numeroFacture()).isEqualTo("FAC-2026-000001");
        gestion.ajouterLigne(ouverte.id(), ligne("5000.00", "4000.00", "500.00", "500.00"));
        gestion.ajouterLigne(ouverte.id(), ligne("1000.00", "800.00", "100.00", "100.00"));
        gestion.changerStatut(ouverte.id(), FactureStatut.EN_COURS, T0.plusMinutes(20));
        gestion.changerStatut(ouverte.id(), FactureStatut.PRESCRITE, T0.plusMinutes(40));
        gestion.changerStatut(ouverte.id(), FactureStatut.MEDICAMENTS_DELIVRES, T0.plusMinutes(50));
        Facture finale = gestion.changerStatut(ouverte.id(), FactureStatut.FINALISEE, T0.plusMinutes(60));

        FactureEcrite ecrite = (FactureEcrite) gestion.clore(ouverte.id()).orElseThrow();

        assertThat(ecrite.facture()).isEqualTo(finale);
        assertThat(finale.montantTotal()).isEqualByComparingTo("6000.00");
        assertThat(finale.partCnam()).isEqualByComparingTo("4800.00");
        assertThat(finale.dateFinalisation()).isEqualTo(T0.plusMinutes(60));
        assertThat(ecrite.lignes()).extracting(FactureLigne::numeroLigne).containsExactly(1, 2);
        assertThat(ecrite.historique()).extracting(FactureHistorique::nouveauStatut).containsExactly(
                FactureStatut.OUVERTE, FactureStatut.EN_COURS, FactureStatut.PRESCRITE,
                FactureStatut.MEDICAMENTS_DELIVRES, FactureStatut.FINALISEE);
        assertThat(ecrite.historique().get(0).ancienStatut()).isNull();
        assertThat(ecrite.historique().get(1).ancienStatut()).isEqualTo(FactureStatut.OUVERTE);
    }

    @Test
    void lesTransitionsInterditesEtLesLignesSurUneFactureTerminaleSontRefusees() {
        GestionFactureMemoire gestion = gestion();
        Facture f = gestion.ouvrir(demande());

        assertThatThrownBy(() -> gestion.changerStatut(f.id(), FactureStatut.FINALISEE, T0))
                .isInstanceOf(TransitionInterditeException.class);

        gestion.changerStatut(f.id(), FactureStatut.ABANDONNEE, T0.plusMinutes(5));
        assertThatThrownBy(() -> gestion.ajouterLigne(f.id(), ligne("100.00", "80.00", "10.00", "10.00")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal");
        // La transition refusee n'a rien laisse dans l'historique.
        FactureEcrite ecrite = (FactureEcrite) gestion.clore(f.id()).orElseThrow();
        assertThat(ecrite.historique()).extracting(FactureHistorique::nouveauStatut)
                .containsExactly(FactureStatut.OUVERTE, FactureStatut.ABANDONNEE);
    }

    @Test
    void uneFactureRefuseeEstTerminaleDesLaCreation() {
        GestionFactureMemoire gestion = gestion();
        Facture refusee = gestion.refuser(demande());

        assertThat(refusee.statut()).isEqualTo(FactureStatut.REFUSEE);
        EcritureAnnexe ecrite = gestion.clore(refusee.id()).orElseThrow();
        assertThat(((FactureEcrite) ecrite).lignes()).isEmpty();
        assertThat(((FactureEcrite) ecrite).historique()).hasSize(1);
    }

    @Test
    void cloreRendLaFactureUneSeuleFoisEtOubliePuisUneFactureInconnueEstRefusee() {
        GestionFactureMemoire gestion = gestion();
        Facture f = gestion.ouvrir(demande());

        assertThat(gestion.clore(f.id())).isPresent();
        assertThat(gestion.clore(f.id())).as("deja close : plus rien en memoire").isEmpty();
        assertThatThrownBy(() -> gestion.ajouterLigne(f.id(), ligne("100.00", "80.00", "10.00", "10.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inconnue");
    }

    @Test
    void lesIdentifiantsSontDerivesDuRunEtDuNumeroPasAleatoires() {
        FactureEcrite a = ecrireUnParcours(gestion());
        FactureEcrite b = ecrireUnParcours(gestion());
        FactureEcrite suivante = ecrireUnParcours(new GestionFactureMemoire(numerotationSansBase(2)));

        assertThat(a).as("meme numero, meme run : memes identifiants").isEqualTo(b);
        assertThat(suivante.facture().id()).isNotEqualTo(a.facture().id());
        assertThat(suivante.lignes().get(0).id()).isNotEqualTo(a.lignes().get(0).id());
    }

    private static FactureEcrite ecrireUnParcours(GestionFactureMemoire gestion) {
        Facture f = gestion.ouvrir(demande());
        gestion.ajouterLigne(f.id(), ligne("100.00", "80.00", "10.00", "10.00"));
        gestion.changerStatut(f.id(), FactureStatut.EN_COURS, T0.plusMinutes(5));
        return (FactureEcrite) gestion.clore(f.id()).orElseThrow();
    }
}

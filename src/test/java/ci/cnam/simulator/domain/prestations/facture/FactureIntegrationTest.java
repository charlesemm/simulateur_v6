package ci.cnam.simulator.domain.prestations.facture;

import ci.cnam.simulator.SupportRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Facture sur PostgreSQL : ouverture, ligne, parcours complet jusqu'a FINALISEE, historique, contraintes. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FactureIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"));

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 1, 8, 0);

    @Autowired
    FactureService service;
    @Autowired
    JdbcTemplate jdbc;

    private NouvelleFacture demande() {
        UUID assure = jdbc.queryForObject("select id from assure limit 1", UUID.class);
        UUID centre = jdbc.queryForObject("select id from centre_sante limit 1", UUID.class);
        return new NouvelleFacture(SupportRun.nouveauRun(jdbc), assure, assure, centre, TypeBon.AMBULATOIRE, T0);
    }

    private NouvelleLigne ligne(String montant, String cnam, String complementaire, String assure) {
        return new NouvelleLigne("C01", "Consultation", T0.plusMinutes(10), 1, new BigDecimal(montant),
                new BigDecimal(montant), new BigDecimal(cnam), new BigDecimal(complementaire),
                new BigDecimal(assure));
    }

    @Test
    void ouvrirAjouterUneLigneEtEnchainerJusquaFinalisee() {
        Facture ouverte = service.ouvrir(demande());
        assertThat(ouverte.statut()).isEqualTo(FactureStatut.OUVERTE);
        assertThat(ouverte.numeroFacture()).matches("FAC-2026-\\d{6}");

        FactureLigne ligne = service.ajouterLigne(ouverte.id(), ligne("5000.00", "4000.00", "500.00", "500.00"));
        assertThat(ligne.numeroLigne()).isEqualTo(1);
        service.ajouterLigne(ouverte.id(), ligne("1000.50", "800.40", "0.00", "200.10"));

        service.changerStatut(ouverte.id(), FactureStatut.EN_COURS, T0.plusMinutes(5));
        service.changerStatut(ouverte.id(), FactureStatut.PRESCRITE, T0.plusMinutes(60));
        service.changerStatut(ouverte.id(), FactureStatut.MEDICAMENTS_DELIVRES, T0.plusMinutes(90));
        LocalDateTime fin = T0.plusMinutes(120);
        service.changerStatut(ouverte.id(), FactureStatut.FINALISEE, fin);

        Facture finale = service.trouver(ouverte.id());
        assertThat(finale.statut()).isEqualTo(FactureStatut.FINALISEE);
        assertThat(finale.dateFinalisation()).isEqualTo(fin);
        assertThat(finale.montantTotal()).isEqualByComparingTo("6000.50");
        assertThat(finale.partCnam()).isEqualByComparingTo("4800.40");
        assertThat(finale.partAssuranceComplementaire()).isEqualByComparingTo("500.00");
        assertThat(finale.partAssure()).isEqualByComparingTo("700.10");
        assertThat(service.lignes(ouverte.id())).hasSize(2);

        List<FactureHistorique> historique = service.historique(ouverte.id());
        assertThat(historique).extracting(FactureHistorique::ancienStatut, FactureHistorique::nouveauStatut)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(null, FactureStatut.OUVERTE),
                        org.assertj.core.groups.Tuple.tuple(FactureStatut.OUVERTE, FactureStatut.EN_COURS),
                        org.assertj.core.groups.Tuple.tuple(FactureStatut.EN_COURS, FactureStatut.PRESCRITE),
                        org.assertj.core.groups.Tuple.tuple(FactureStatut.PRESCRITE,
                                FactureStatut.MEDICAMENTS_DELIVRES),
                        org.assertj.core.groups.Tuple.tuple(FactureStatut.MEDICAMENTS_DELIVRES,
                                FactureStatut.FINALISEE));
        assertThat(historique).allSatisfy(h -> {
            assertThat(h.horodatageSimule()).isNotNull();
            assertThat(h.horodatageReel()).isNotNull();
        });
    }

    @Test
    void transitionInterditeNEcritRienEtLaisseLeStatut() {
        Facture f = service.ouvrir(demande());
        assertThatThrownBy(() -> service.changerStatut(f.id(), FactureStatut.FINALISEE, T0))
                .isInstanceOf(TransitionInterditeException.class);
        assertThat(service.trouver(f.id()).statut()).isEqualTo(FactureStatut.OUVERTE);
        assertThat(service.historique(f.id())).hasSize(1);
    }

    @Test
    void factureRefuseeCreeeDirectementEstTerminale() {
        Facture f = service.refuser(demande());
        assertThat(f.statut()).isEqualTo(FactureStatut.REFUSEE);
        assertThat(service.historique(f.id())).hasSize(1);
        assertThatThrownBy(() -> service.changerStatut(f.id(), FactureStatut.EN_COURS, T0))
                .isInstanceOf(TransitionInterditeException.class);
        assertThatThrownBy(() -> service.ajouterLigne(f.id(), ligne("100.00", "100.00", "0.00", "0.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void numerosDeFactureUniquesEtCroissants() {
        Facture a = service.ouvrir(demande());
        Facture b = service.ouvrir(demande());
        assertThat(a.numeroFacture()).isNotEqualTo(b.numeroFacture());
        assertThat(b.numeroFacture()).isGreaterThan(a.numeroFacture());
    }

    @Test
    void lesContraintesCheckRefusentUnStatutOuUnTypeInconnu() {
        Facture f = service.ouvrir(demande());
        assertThatThrownBy(() -> jdbc.update("update facture set statut = 'INCONNU' where id = ?", f.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update facture set type_bon = 'AUTRE' where id = ?", f.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ligneDontLesPartsNeFontPasLeMontantEstRefusee() {
        Facture f = service.ouvrir(demande());
        assertThatThrownBy(() -> service.ajouterLigne(f.id(), ligne("100.00", "50.00", "0.00", "0.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

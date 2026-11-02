package ci.cnam.simulator.domain.prestations.tarification;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.SupportRun;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureLigne;
import ci.cnam.simulator.domain.prestations.facture.FactureService;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursOrchestrator;
import ci.cnam.simulator.domain.reference.ReferenceService;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Taux lus dans PostgreSQL (migration V5), modifiables sans recompiler, et parcours complet tarife. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TarificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"));

    @Autowired
    TarificationService tarification;
    @Autowired
    TauxRepository taux;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ParcoursOrchestrator orchestrateur;
    @Autowired
    ReferenceService reference;
    @Autowired
    FactureService factureService;

    @Test
    void lesSixTauxFictifsSontLusDansLaBase() {
        RegleTaux regle = taux.charger();

        assertThat(jdbc.queryForObject("select count(*) from taux", Integer.class)).isEqualTo(6);
        for (TypePrestation type : TypePrestation.values()) {
            assertThat(regle.tauxCnam(type)).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        }
        assertThat(regle.tauxComplementaire()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void unTauxModifieEnBaseEstPrisEnCompteApresRechargement() {
        BigDecimal avant = taux.charger().tauxCnam(TypePrestation.DENTAIRE);
        try {
            jdbc.update("update taux set taux = 0.1000 where code = 'CNAM_DENTAIRE'");
            tarification.recharger();

            Repartition r = tarification.repartir(new BigDecimal("1000.00"), TypePrestation.DENTAIRE);
            assertThat(r.partCnam()).isEqualByComparingTo("100.00");
        } finally {
            jdbc.update("update taux set taux = ? where code = 'CNAM_DENTAIRE'", avant);
            tarification.recharger();
        }
    }

    @Test
    void lesFacturesDUnParcoursSontTarifeesEtLesTotauxSontLaSommeDesLignes() {
        SeededRandom random = new SeededRandom(11);
        UUID run = SupportRun.nouveauRun(jdbc);
        int facturesVerifiees = 0;
        for (int i = 0; i < 60 && facturesVerifiees < 10; i++) {
            CentreSante centre = reference.centreAleatoire(random);
            var resultat = orchestrateur.executer(run, reference.assureAleatoire(random), centre,
                    reference.medecinAleatoire(centre, random), reference.pharmacieAleatoire(random), random);
            List<FactureLigne> lignes = factureService.lignes(resultat.facture().id());
            if (lignes.isEmpty()) {
                continue; // acces refuse : pas de ligne
            }
            Facture f = factureService.trouver(resultat.facture().id());
            for (FactureLigne l : lignes) {
                assertThat(l.partCnam().add(l.partAssuranceComplementaire()).add(l.partAssure()))
                        .isEqualByComparingTo(l.montant());
                assertThat(l.montant()).isEqualByComparingTo(l.prixUnitaire().multiply(BigDecimal.valueOf(l.quantite())));
                assertThat(l.prixUnitaire().signum()).isPositive(); // tarif du catalogue
            }
            assertThat(f.montantTotal()).isEqualByComparingTo(lignes.stream().map(FactureLigne::montant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            assertThat(f.partCnam()).isEqualByComparingTo(lignes.stream().map(FactureLigne::partCnam)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            assertThat(f.partAssure()).isEqualByComparingTo(lignes.stream().map(FactureLigne::partAssure)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            facturesVerifiees++;
        }
        assertThat(facturesVerifiees).isEqualTo(10);
    }
}

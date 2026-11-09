package ci.cnam.simulator.domain.prestations.tarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.facture.NouvelleLigne;
import ci.cnam.simulator.domain.reference.model.Regime;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Calculs chiffres. Les taux sont ceux du test (jamais ceux de la base) : la regle est construite ici. */
class TarificationServiceTest {

    /**
     * Plancher de regime neutre : les deux regimes a 0, donc le plancher ne joue jamais. Ces tests
     * portent sur l'axe "type de prestation" et sur les arrondis ; le plancher a son test a lui.
     */
    private static final Map<Regime, TauxRegime> REGIMES_NEUTRES = Map.of(
            Regime.GENERAL_DE_BASE, new TauxRegime(BigDecimal.ZERO, true),
            Regime.ASSISTANCE_MEDICALE, new TauxRegime(BigDecimal.ZERO, false));

    private static TarificationService service(String tauxCnam, String tauxComplementaire) {
        Map<TypePrestation, BigDecimal> cnam = new EnumMap<>(TypePrestation.class);
        for (TypePrestation type : TypePrestation.values()) {
            cnam.put(type, new BigDecimal(tauxCnam));
        }
        return new TarificationService(new RegleTaux(cnam, new BigDecimal(tauxComplementaire), REGIMES_NEUTRES));
    }

    @ParameterizedTest(name = "{0} FCFA, CNAM {1}, compl. {2} -> {3} / {4} / {5}")
    @CsvSource({
            // montant, taux CNAM, taux complementaire, part CNAM, part compl., part assure
            "100.00,   0.70, 0.50, 70.00,    15.00,   15.00",
            "100.01,   0.70, 0.50, 70.01,    15.00,   15.00",   // 70,007 arrondi a 70,01
            "100.01,   0.70, 0.00, 70.01,    0.00,    30.00",
            "0.00,     0.70, 0.50, 0.00,     0.00,    0.00",     // montant nul
            "5000.00,  0.70, 0.50, 3500.00,  750.00,  750.00",
            "0.01,     0.70, 0.50, 0.01,     0.00,    0.00",     // 0,007 -> 0,01
            "0.03,     0.70, 0.50, 0.02,     0.01,    0.00",     // 0,021 -> 0,02 ; 0,005 -> 0,01
            "0.05,     0.50, 0.50, 0.03,     0.01,    0.01",     // 0,025 -> 0,03 (HALF_UP)
            "333.33,   0.70, 0.50, 233.33,   50.00,   50.00",
            "1234.57,  0.65, 0.50, 802.47,   216.05,  216.05",
            "999.99,   0.80, 0.50, 799.99,   100.00,  100.00",
            "100.00,   1.00, 0.50, 100.00,   0.00,    0.00",     // prise en charge totale
            "100.00,   0.00, 0.50, 0.00,     50.00,   50.00",    // aucune prise en charge CNAM
            "100.00,   0.00, 0.00, 0.00,     0.00,    100.00",   // l'assure paie tout
            "12345.67, 0.90, 0.30, 11111.10, 370.37,  864.20",
            "7.50,     0.70, 0.50, 5.25,     1.13,    1.12",     // 1,125 -> 1,13 ; le reliquat va a l'assure
            "25000.00, 0.90, 0.50, 22500.00, 1250.00, 1250.00",
            "1.01,     0.50, 0.50, 0.51,     0.25,    0.25",
            "45.45,    0.70, 0.33, 31.82,    4.50,    9.13"
    })
    void repartitionChiffree(String montant, String tauxCnam, String tauxCompl, String cnam, String compl,
                             String assure) {
        Repartition r = service(tauxCnam, tauxCompl).repartir(new BigDecimal(montant), TypePrestation.CONSULTATION, Regime.GENERAL_DE_BASE);

        assertThat(r.partCnam()).isEqualByComparingTo(cnam);
        assertThat(r.partAssuranceComplementaire()).isEqualByComparingTo(compl);
        assertThat(r.partAssure()).isEqualByComparingTo(assure);
        assertThat(r.montant()).isEqualByComparingTo(montant);
        assertThat(r.partCnam().add(r.partAssuranceComplementaire()).add(r.partAssure()))
                .isEqualByComparingTo(r.montant());
        // Echelle 2 partout.
        assertThat(r.partCnam().scale()).isEqualTo(2);
        assertThat(r.partAssuranceComplementaire().scale()).isEqualTo(2);
        assertThat(r.partAssure().scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "tarif {0} x {1} = {2} (CNAM {3}, compl. {4}, assure {5})")
    @CsvSource({
            // tarif unitaire, quantite, montant, part CNAM, part compl., part assure (taux 0,70 / 0,50)
            "5000.00, 1, 5000.00,  3500.00,  750.00,  750.00",
            "5000.00, 5, 25000.00, 17500.00, 3750.00, 3750.00",
            "100.01,  3, 300.03,   210.02,   45.01,   45.00",    // 210,021 -> 210,02 ; 45,005 -> 45,01
            "0.00,    4, 0.00,     0.00,     0.00,    0.00"
    })
    void quantiteSuperieureAUn(String tarif, int quantite, String montant, String cnam, String compl,
                               String assure) {
        Repartition r = service("0.70", "0.50").repartir(new BigDecimal(tarif), quantite, TypePrestation.MEDICAMENT, Regime.GENERAL_DE_BASE);

        assertThat(r.montant()).isEqualByComparingTo(montant);
        assertThat(r.partCnam()).isEqualByComparingTo(cnam);
        assertThat(r.partAssuranceComplementaire()).isEqualByComparingTo(compl);
        assertThat(r.partAssure()).isEqualByComparingTo(assure);
    }

    @Test
    void lePlancherDuRegimeNePeutQueReleverLaPriseEnCharge() {
        // Les taux reels : le regime general est pris en charge a 70 %, l'assistance medicale a 100 %.
        Map<TypePrestation, BigDecimal> parType = Map.of(
                TypePrestation.CONSULTATION, new BigDecimal("0.70"),
                TypePrestation.EXAMEN, new BigDecimal("0.80"),
                TypePrestation.HOSPITALISATION, new BigDecimal("0.90"),
                TypePrestation.MEDICAMENT, new BigDecimal("0.65"),
                TypePrestation.DENTAIRE, new BigDecimal("0.50"));
        TarificationService service = new TarificationService(new RegleTaux(parType, new BigDecimal("0.50"),
                Map.of(Regime.GENERAL_DE_BASE, new TauxRegime(new BigDecimal("0.70"), true),
                        Regime.ASSISTANCE_MEDICALE, new TauxRegime(BigDecimal.ONE, false))));
        BigDecimal mille = new BigDecimal("1000.00");

        // Regime general : les types fixes sous 70 % sont releves, ceux fixes au-dessus gardent leur taux.
        assertThat(service.repartir(mille, TypePrestation.DENTAIRE, Regime.GENERAL_DE_BASE).partCnam())
                .as("dentaire a 50 %, releve au plancher de 70 %").isEqualByComparingTo("700.00");
        assertThat(service.repartir(mille, TypePrestation.MEDICAMENT, Regime.GENERAL_DE_BASE).partCnam())
                .as("medicament a 65 %, releve au plancher de 70 %").isEqualByComparingTo("700.00");
        assertThat(service.repartir(mille, TypePrestation.HOSPITALISATION, Regime.GENERAL_DE_BASE).partCnam())
                .as("hospitalisation a 90 %, au-dessus du plancher : inchangee").isEqualByComparingTo("900.00");

        // Assistance medicale : 100 % partout, donc plus de ticket moderateur a partager.
        for (TypePrestation type : TypePrestation.values()) {
            Repartition r = service.repartir(mille, type, Regime.ASSISTANCE_MEDICALE);
            assertThat(r.partCnam()).as(type.name()).isEqualByComparingTo("1000.00");
            assertThat(r.partAssure()).as(type.name()).isEqualByComparingTo("0.00");
            assertThat(r.partAssuranceComplementaire()).as(type.name()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void chaqueTypeDePrestationUtiliseSonPropreTaux() {
        TarificationService service = new TarificationService(new RegleTaux(Map.of(
                TypePrestation.CONSULTATION, new BigDecimal("0.70"),
                TypePrestation.EXAMEN, new BigDecimal("0.80"),
                TypePrestation.HOSPITALISATION, new BigDecimal("0.90"),
                TypePrestation.MEDICAMENT, new BigDecimal("0.65"),
                TypePrestation.DENTAIRE, new BigDecimal("0.50")), new BigDecimal("0.50"), REGIMES_NEUTRES));
        BigDecimal mille = new BigDecimal("1000.00");

        assertThat(service.repartir(mille, TypePrestation.CONSULTATION, Regime.GENERAL_DE_BASE).partCnam()).isEqualByComparingTo("700.00");
        assertThat(service.repartir(mille, TypePrestation.EXAMEN, Regime.GENERAL_DE_BASE).partCnam()).isEqualByComparingTo("800.00");
        assertThat(service.repartir(mille, TypePrestation.HOSPITALISATION, Regime.GENERAL_DE_BASE).partCnam()).isEqualByComparingTo("900.00");
        assertThat(service.repartir(mille, TypePrestation.MEDICAMENT, Regime.GENERAL_DE_BASE).partCnam()).isEqualByComparingTo("650.00");
        assertThat(service.repartir(mille, TypePrestation.DENTAIRE, Regime.GENERAL_DE_BASE).partCnam()).isEqualByComparingTo("500.00");
    }

    @Test
    void laLigneConstruiteVientDuTarifDuCatalogueEtPorteSesTroisParts() {
        NouvelleLigne ligne = service("0.70", "0.50").ligne("ACT-001", "Consultation generaliste",
                LocalDateTime.of(2026, 3, 10, 9, 0), 2, new BigDecimal("5000"), TypePrestation.CONSULTATION, Regime.GENERAL_DE_BASE);

        assertThat(ligne.quantite()).isEqualTo(2);
        assertThat(ligne.prixUnitaire()).isEqualByComparingTo("5000.00");
        assertThat(ligne.montant()).isEqualByComparingTo("10000.00");
        assertThat(ligne.partCnam()).isEqualByComparingTo("7000.00");
        assertThat(ligne.partAssuranceComplementaire()).isEqualByComparingTo("1500.00");
        assertThat(ligne.partAssure()).isEqualByComparingTo("1500.00");
    }

    @Test
    void montantNegatifOuQuantiteNulleSontRefuses() {
        TarificationService service = service("0.70", "0.50");

        assertThatThrownBy(() -> service.repartir(new BigDecimal("-1.00"), TypePrestation.EXAMEN, Regime.GENERAL_DE_BASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.repartir(new BigDecimal("10.00"), 0, TypePrestation.EXAMEN, Regime.GENERAL_DE_BASE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uneRegleIncompleteOuHorsBornesEstRefusee() {
        assertThatThrownBy(() -> new RegleTaux(Map.of(TypePrestation.CONSULTATION, new BigDecimal("0.70")),
                new BigDecimal("0.50"), REGIMES_NEUTRES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegleTaux.depuis(Map.of(), REGIMES_NEUTRES)).isInstanceOf(IllegalArgumentException.class);
        Map<TypePrestation, BigDecimal> trop = new EnumMap<>(TypePrestation.class);
        for (TypePrestation t : TypePrestation.values()) {
            trop.put(t, new BigDecimal("1.01"));
        }
        assertThatThrownBy(() -> new RegleTaux(trop, new BigDecimal("0.50"), REGIMES_NEUTRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lInvariantDesTroisPartsTientSurMilleLignesAleatoires() {
        SeededRandom random = new SeededRandom(2026L);
        for (int i = 0; i < 1000; i++) {
            BigDecimal tauxCnam = BigDecimal.valueOf(random.entre(0, 10000), 4);
            BigDecimal tauxCompl = BigDecimal.valueOf(random.entre(0, 10000), 4);
            TarificationService service = service(tauxCnam.toPlainString(), tauxCompl.toPlainString());
            TypePrestation type = TypePrestation.values()[random.entre(0, TypePrestation.values().length - 1)];
            BigDecimal tarif = BigDecimal.valueOf(random.entre(0, 5_000_000), 2);
            int quantite = random.entre(1, 10);

            Repartition r = service.repartir(tarif, quantite, type, Regime.GENERAL_DE_BASE);

            assertThat(r.partCnam().add(r.partAssuranceComplementaire()).add(r.partAssure()))
                    .as("ligne %d : tarif %s x %d, taux %s / %s", i, tarif, quantite, tauxCnam, tauxCompl)
                    .isEqualByComparingTo(r.montant());
            assertThat(r.partCnam().signum()).isGreaterThanOrEqualTo(0);
            assertThat(r.partAssuranceComplementaire().signum()).isGreaterThanOrEqualTo(0);
            assertThat(r.partAssure().signum()).isGreaterThanOrEqualTo(0);
        }
    }
}

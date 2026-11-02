package ci.cnam.simulator.domain.prestations.droits;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.domain.prestations.droits.ResultatDroits.MotifRefus;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.Genre;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControleDroitsServiceTest {

    private static final LocalDate SOIN = LocalDate.of(2026, 3, 10);
    private final ControleDroitsService service = new ControleDroitsService();

    private static Assure assure(StatutCotisation cotisation, LocalDate debut, LocalDate fin) {
        return new Assure(UUID.randomUUID(), "A1", "Nom", "Prenoms", LocalDate.of(1990, 1, 1), Genre.F, cotisation,
                debut, fin);
    }

    @Test
    void droitsOuvertsSansFinEtCotisationsAJourSontAcceptes() {
        assertThat(service.controler(assure(StatutCotisation.A_JOUR, LocalDate.of(2020, 1, 1), null), SOIN).accorde())
                .isTrue();
    }

    @Test
    void lesBornesDesDroitsSontIncluses() {
        assertThat(service.controler(assure(StatutCotisation.A_JOUR, SOIN, SOIN), SOIN).accorde()).isTrue();
    }

    @Test
    void droitsTerminesLaVeilleSontFermes() {
        ResultatDroits r = service.controler(assure(StatutCotisation.A_JOUR, LocalDate.of(2020, 1, 1), SOIN.minusDays(1)),
                SOIN);
        assertThat(r.accorde()).isFalse();
        assertThat(r.motif()).isEqualTo(MotifRefus.DROITS_FERMES);
    }

    @Test
    void droitsOuvertsLeLendemainSontFermes() {
        ResultatDroits r = service.controler(assure(StatutCotisation.A_JOUR, SOIN.plusDays(1), null), SOIN);
        assertThat(r.motif()).isEqualTo(MotifRefus.DROITS_FERMES);
    }

    @Test
    void cotisationsEnRetardSontRefusees() {
        ResultatDroits r = service.controler(assure(StatutCotisation.EN_RETARD, LocalDate.of(2020, 1, 1), null), SOIN);
        assertThat(r.accorde()).isFalse();
        assertThat(r.motif()).isEqualTo(MotifRefus.COTISATIONS_EN_RETARD);
    }
}

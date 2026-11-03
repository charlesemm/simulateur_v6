package ci.cnam.simulator.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnomalyQueryServiceTest {

    /** Magasin espion : retient les criteres recus. */
    private static final class Espion implements AnomalyQueryStore {
        AnomalyType type;
        String champ;
        int page;
        int taille;

        @Override
        public List<AnomalyRecord> rechercher(UUID runId, AnomalyType type, String champ, int page, int taille) {
            this.type = type;
            this.champ = champ;
            this.page = page;
            this.taille = taille;
            return new ArrayList<>();
        }

        @Override
        public long compter(UUID runId, AnomalyType type, String champ) {
            return 120;
        }
    }

    private final Espion store = new Espion();
    private final AnomalyQueryService service = new AnomalyQueryService(store);
    private final UUID run = UUID.randomUUID();

    @Test
    void lesFiltresVidesNeFiltrentPas() {
        service.rechercher(run, " ", "", null, null);

        assertThat(store.type).isNull();
        assertThat(store.champ).isNull();
        assertThat(store.page).isZero();
        assertThat(store.taille).isEqualTo(50);
    }

    @Test
    void leTypeSAccepteEnNomOuEnCleDeScenario() {
        service.rechercher(run, "CHAMP_VIDE", " nom ", 2, 10);
        assertThat(store.type).isEqualTo(AnomalyType.CHAMP_VIDE);
        assertThat(store.champ).isEqualTo("nom");
        assertThat(store.page).isEqualTo(2);

        service.rechercher(run, "fauteOrthographe", null, 0, 10);
        assertThat(store.type).isEqualTo(AnomalyType.FAUTE_ORTHOGRAPHE);
    }

    @Test
    void lAtailleDePageEstBorneeEtLePageNeDevientJamaisNegatif() {
        service.rechercher(run, null, null, -4, 100_000);
        assertThat(store.page).isZero();
        assertThat(store.taille).isEqualTo(AnomalyQueryService.TAILLE_PAGE_MAX);

        service.rechercher(run, null, null, 0, 0);
        assertThat(store.taille).isEqualTo(1);
    }

    @Test
    void lePageCalculeLeNombreDePages() {
        AnomalyQueryService.Page page = service.rechercher(run, null, null, 0, 50);

        assertThat(page.total()).isEqualTo(120);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void unTypeInconnuEstRefuseEnListantLesValeursPermises() {
        assertThatThrownBy(() -> service.rechercher(run, "N_IMPORTE_QUOI", null, 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("N_IMPORTE_QUOI")
                .hasMessageContaining("CHAMP_VIDE");
    }
}

package ci.cnam.simulator.domain.reference.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NumeroAssureTest {

    @Test
    void numeroGenereEstValideSurDixChiffres() {
        for (int base : new int[] {0, 1, 96, 97, 98, 12345678, 99_999_999}) {
            String numero = NumeroAssure.depuisBase(base);
            assertThat(numero).hasSize(10).containsOnlyDigits();
            assertThat(NumeroAssure.estValide(numero)).isTrue();
        }
    }

    @Test
    void cleDeControleCalculee() {
        // 12345678 mod 97 = 3 -> cle = 94
        assertThat(NumeroAssure.depuisBase(12345678)).isEqualTo("1234567894");
    }

    @Test
    void numeroAvecMauvaiseCleOuMauvaisFormatEstInvalide() {
        assertThat(NumeroAssure.estValide("1234567859")).isFalse();
        assertThat(NumeroAssure.estValide("123456785")).isFalse();
        assertThat(NumeroAssure.estValide("12345678AB")).isFalse();
        assertThat(NumeroAssure.estValide(null)).isFalse();
    }

    @Test
    void baseHorsLimitesRefusee() {
        assertThatThrownBy(() -> NumeroAssure.depuisBase(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NumeroAssure.depuisBase(100_000_000)).isInstanceOf(IllegalArgumentException.class);
    }
}

package ci.cnam.simulator.core.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SeededRandomTest {

    @Test
    void memeGraineMemeSuite() {
        SeededRandom a = new SeededRandom(42);
        SeededRandom b = new SeededRandom(42);
        assertThat(IntStream.range(0, 50).mapToObj(i -> a.nextUuid()).toList())
                .isEqualTo(IntStream.range(0, 50).mapToObj(i -> b.nextUuid()).toList());
    }

    @Test
    void grainesDifferentesSuitesDifferentes() {
        assertThat(new SeededRandom(1).nextUuid()).isNotEqualTo(new SeededRandom(2).nextUuid());
    }

    @Test
    void entreRespecteLesBornesInclusives() {
        SeededRandom r = new SeededRandom(1);
        List<Integer> tirages = IntStream.range(0, 1000).map(i -> r.entre(3, 5)).boxed().toList();
        assertThat(tirages).allMatch(n -> n >= 3 && n <= 5).contains(3, 4, 5);
    }

    @Test
    void uuidEstDeVersion4() {
        assertThat(new SeededRandom(9).nextUuid().version()).isEqualTo(4);
    }
}

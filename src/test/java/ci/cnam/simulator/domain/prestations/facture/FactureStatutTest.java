package ci.cnam.simulator.domain.prestations.facture;

import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.ABANDONNEE;
import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.EN_COURS;
import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.FINALISEE;
import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.MEDICAMENTS_DELIVRES;
import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.OUVERTE;
import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.PRESCRITE;
import static ci.cnam.simulator.domain.prestations.facture.FactureStatut.REFUSEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FactureStatutTest {

    private static final Set<List<FactureStatut>> AUTORISEES = Set.of(
            List.of(OUVERTE, EN_COURS),
            List.of(EN_COURS, PRESCRITE),
            List.of(PRESCRITE, MEDICAMENTS_DELIVRES),
            List.of(MEDICAMENTS_DELIVRES, FINALISEE),
            List.of(OUVERTE, ABANDONNEE),
            List.of(EN_COURS, ABANDONNEE),
            List.of(PRESCRITE, ABANDONNEE));

    static Stream<Arguments> transitionsAutorisees() {
        return Stream.of(
                Arguments.of(OUVERTE, EN_COURS),
                Arguments.of(EN_COURS, PRESCRITE),
                Arguments.of(PRESCRITE, MEDICAMENTS_DELIVRES),
                Arguments.of(MEDICAMENTS_DELIVRES, FINALISEE),
                Arguments.of(OUVERTE, ABANDONNEE),
                Arguments.of(EN_COURS, ABANDONNEE),
                Arguments.of(PRESCRITE, ABANDONNEE));
    }

    static Stream<Arguments> transitionsInterdites() {
        return Stream.of(
                Arguments.of(OUVERTE, PRESCRITE),            // saut d'etape
                Arguments.of(OUVERTE, FINALISEE),            // saut d'etape
                Arguments.of(EN_COURS, OUVERTE),             // retour arriere
                Arguments.of(PRESCRITE, EN_COURS),           // retour arriere
                Arguments.of(MEDICAMENTS_DELIVRES, ABANDONNEE), // abandon impossible apres delivrance
                Arguments.of(FINALISEE, OUVERTE),            // etat terminal
                Arguments.of(FINALISEE, ABANDONNEE),         // etat terminal
                Arguments.of(ABANDONNEE, EN_COURS),          // etat terminal
                Arguments.of(REFUSEE, OUVERTE),              // etat terminal
                Arguments.of(OUVERTE, REFUSEE),              // REFUSEE n'est que creee directement
                Arguments.of(EN_COURS, EN_COURS));           // sur place
    }

    @ParameterizedTest
    @MethodSource("transitionsAutorisees")
    void transitionAutoriseeAcceptee(FactureStatut depuis, FactureStatut vers) {
        assertThat(depuis.peutPasserA(vers)).isTrue();
        assertThatCode(() -> depuis.verifierTransitionVers(vers)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("transitionsInterdites")
    void transitionInterditeLeveUneExceptionClaire(FactureStatut depuis, FactureStatut vers) {
        assertThat(depuis.peutPasserA(vers)).isFalse();
        assertThatThrownBy(() -> depuis.verifierTransitionVers(vers))
                .isInstanceOf(TransitionInterditeException.class)
                .hasMessage("Transition interdite : " + depuis + " -> " + vers)
                .extracting(e -> ((TransitionInterditeException) e).ancien(),
                        e -> ((TransitionInterditeException) e).nouveau())
                .containsExactly(depuis, vers);
    }

    @Test
    void ensembleDesTransitionsEstExactementCeluiDuCahierDesCharges() {
        for (FactureStatut depuis : FactureStatut.values()) {
            for (FactureStatut vers : FactureStatut.values()) {
                boolean attendue = AUTORISEES.contains(List.of(depuis, vers));
                assertThat(depuis.peutPasserA(vers)).as("%s -> %s", depuis, vers).isEqualTo(attendue);
            }
        }
    }

    @Test
    void etatsTerminaux() {
        assertThat(Stream.of(FactureStatut.values()).filter(FactureStatut::estTerminal))
                .containsExactlyInAnyOrder(FINALISEE, REFUSEE, ABANDONNEE);
    }

    @Test
    void factureAvecStatutRenseigneLaFinalisationSeulementALaFinalisation() {
        LocalDateTime ouverture = LocalDateTime.of(2026, 3, 1, 8, 0);
        LocalDateTime fin = LocalDateTime.of(2026, 3, 1, 11, 0);
        Facture f = new Facture(UUID.randomUUID(), UUID.randomUUID(), "FAC-2026-000001", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), TypeBon.AMBULATOIRE, MEDICAMENTS_DELIVRES, ouverture, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(f.avecStatut(FINALISEE, fin).dateFinalisation()).isEqualTo(fin);
        assertThatThrownBy(() -> f.avecStatut(OUVERTE, fin)).isInstanceOf(TransitionInterditeException.class);
    }
}

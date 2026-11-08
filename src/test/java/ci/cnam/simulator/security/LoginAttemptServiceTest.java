package ci.cnam.simulator.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.persistence.repository.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

class LoginAttemptServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-05T10:00:00Z");

    private final AppUserRepository utilisateurs = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private LoginAttemptService service;
    private AppUser alice;

    @BeforeEach
    void preparer() {
        SimulateurProperties proprietes = new SimulateurProperties(42, "scenarios",
                new SimulateurProperties.Securite("cle", "mdp", 5, 15), null, null, null, null);
        service = new LoginAttemptService(utilisateurs, audit, Clock.fixed(T0, ZoneOffset.UTC), proprietes);
        alice = new AppUser("alice", "hash", Role.OPERATEUR);
        when(utilisateurs.findByNomUtilisateur("alice")).thenReturn(Optional.of(alice));
        when(utilisateurs.findByNomUtilisateur("inconnu")).thenReturn(Optional.empty());
    }

    private static AuthenticationFailureBadCredentialsEvent echecDe(String nom) {
        return new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken(nom, "x"),
                new org.springframework.security.authentication.BadCredentialsException("x"));
    }

    @Test
    @DisplayName("4 echecs : compteur seulement ; le 5e verrouille le compte 15 minutes")
    void verrouilleAuCinquiemeEchec() {
        for (int i = 0; i < 4; i++) {
            service.echec(echecDe("alice"));
        }
        assertThat(alice.getEchecsConsecutifs()).isEqualTo(4);
        assertThat(alice.verrouille(T0)).isFalse();

        service.echec(echecDe("alice"));

        assertThat(alice.verrouille(T0)).isTrue();
        assertThat(alice.getVerrouilleJusqua()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        assertThat(alice.verrouille(T0.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    @DisplayName("Un compte inconnu est ignore : pas d'erreur, aucune ecriture")
    void compteInconnuIgnore() {
        service.echec(echecDe("inconnu"));
        org.mockito.Mockito.verify(utilisateurs, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Une connexion reussie remet le compteur a zero")
    void succesRemetAZero() {
        service.echec(echecDe("alice"));
        service.echec(echecDe("alice"));
        service.succes(new AuthenticationSuccessEvent(new UsernamePasswordAuthenticationToken("alice", "x")));
        assertThat(alice.getEchecsConsecutifs()).isZero();
        assertThat(alice.getDateDerniereConnexion()).isEqualTo(T0);
    }
}

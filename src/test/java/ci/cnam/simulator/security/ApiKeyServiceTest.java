package ci.cnam.simulator.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.persistence.entity.ApiKey;
import ci.cnam.simulator.persistence.repository.ApiKeyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyServiceTest {

    private final ApiKeyRepository cles = mock(ApiKeyRepository.class);

    private ApiKeyService service(String cleEnvironnement) {
        return new ApiKeyService(cles, new SimulateurProperties(42, "scenarios",
                new SimulateurProperties.Securite(cleEnvironnement, "mdp", 5, 15), null, null, null));
    }

    @Test
    @DisplayName("La cle d'environnement est acceptee sans acces a la base ; une autre cle est refusee")
    void cleEnvironnement() {
        ApiKeyService service = service("ma-cle-secrete");
        assertThat(service.valide("ma-cle-secrete")).isTrue();
        assertThat(service.valide("autre")).isFalse();
        assertThat(service.valide("")).isFalse();
        assertThat(service.valide(null)).isFalse();
    }

    @Test
    @DisplayName("Sans cle configuree, rien n'est accepte")
    void sansCle() {
        assertThat(service("").valide("")).isFalse();
        assertThat(service(null).valide("x")).isFalse();
    }

    @Test
    @DisplayName("Une cle de la base (stockee hachee) est acceptee apres rafraichissement ; desactivee, elle est refusee")
    void cleEnBase() {
        ApiKeyService service = service("ma-cle-secrete");
        ApiKey supplementaire = new ApiKey("outil-qualite", ApiKeyService.empreinte("cle-qualite"));
        when(cles.findAll()).thenReturn(List.of(supplementaire));
        when(cles.findByHash(ApiKeyService.empreinte("ma-cle-secrete"))).thenReturn(Optional.empty());

        service.rafraichir();
        assertThat(service.valide("cle-qualite")).isTrue();
        assertThat(supplementaire.getHash()).doesNotContain("cle-qualite").hasSize(64);

        supplementaire.setActif(false);
        service.rafraichir();
        assertThat(service.valide("cle-qualite")).isFalse();
        assertThat(service.valide("ma-cle-secrete")).isTrue();
    }
}

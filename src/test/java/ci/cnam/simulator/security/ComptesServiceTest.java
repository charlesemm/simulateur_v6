package ci.cnam.simulator.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.persistence.repository.AppUserRepository;
import ci.cnam.simulator.security.ComptesService.ComptesException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ComptesServiceTest {

    private final AppUserRepository utilisateurs = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private ComptesService service;

    @BeforeEach
    void preparer() {
        service = new ComptesService(utilisateurs, encodeur, audit);
        when(utilisateurs.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Creation : mot de passe hache en BCrypt, changement exige, creation auditee sans mot de passe")
    void creer() {
        when(utilisateurs.findByNomUtilisateur("marie")).thenReturn(Optional.empty());

        AppUser cree = service.creer("admin", "Marie", " Marie.Kone@CNAM.CI ", "un-mot-de-passe-long",
                Role.OBSERVATEUR).compte();

        assertThat(cree.getNomUtilisateur()).isEqualTo("marie");
        assertThat(cree.getEmail()).isEqualTo("marie.kone@cnam.ci");
        assertThat(cree.getMotDePasseHash()).startsWith("$2").doesNotContain("un-mot-de-passe-long");
        assertThat(encodeur.matches("un-mot-de-passe-long", cree.getMotDePasseHash())).isTrue();
        assertThat(cree.isMotDePasseAChanger()).isTrue();
        verify(audit).enregistrer("admin", "UTILISATEUR_CREE", "marie",
                "role OBSERVATEUR, email marie.kone@cnam.ci");
    }

    @Test
    @DisplayName("Mot de passe laisse vide : Echo en tire un, assez long et different a chaque fois")
    void motDePasseGenere() {
        when(utilisateurs.findByNomUtilisateur(any())).thenReturn(Optional.empty());

        CompteCree premier = service.creer("admin", "marie", null, "  ", Role.OBSERVATEUR);
        CompteCree second = service.creer("admin", "paul", null, null, Role.OBSERVATEUR);

        assertThat(premier.motDePasseTemporaire()).hasSizeGreaterThanOrEqualTo(ComptesService.LONGUEUR_MIN);
        assertThat(second.motDePasseTemporaire()).isNotEqualTo(premier.motDePasseTemporaire());
        // Le mot de passe rendu est bien celui qui a ete hache : la personne pourra se connecter avec.
        assertThat(encodeur.matches(premier.motDePasseTemporaire(), premier.compte().getMotDePasseHash())).isTrue();
        // Il n'apparait jamais au journal d'audit.
        verify(audit, never()).enregistrer(any(), any(), any(), contains(premier.motDePasseTemporaire()));
    }

    @Test
    @DisplayName("Adresse de messagerie : vide vaut 'aucune', mal formee refusee")
    void adresseDeMessagerie() {
        when(utilisateurs.findByNomUtilisateur(any())).thenReturn(Optional.empty());

        assertThat(service.creer("admin", "marie", "   ", null, Role.OBSERVATEUR).compte().getEmail()).isNull();
        assertThatThrownBy(() -> service.creer("admin", "paul", "pas-une-adresse", null, Role.OBSERVATEUR))
                .isInstanceOf(ComptesException.class).hasMessageContaining("Adresse de messagerie invalide");
    }

    @Test
    @DisplayName("Mot de passe trop court, nom invalide, doublon : refuses")
    void refus() {
        assertThatThrownBy(() -> service.creer("admin", "marie", null, "court", Role.ADMIN))
                .isInstanceOf(ComptesException.class).hasMessageContaining("12 caracteres");
        assertThatThrownBy(() -> service.creer("admin", "M A", null, "un-mot-de-passe-long", Role.ADMIN))
                .isInstanceOf(ComptesException.class).hasMessageContaining("Nom d'utilisateur");
        when(utilisateurs.findByNomUtilisateur("marie"))
                .thenReturn(Optional.of(new AppUser("marie", "h", Role.ADMIN)));
        assertThatThrownBy(() -> service.creer("admin", "marie", null, "un-mot-de-passe-long", Role.ADMIN))
                .isInstanceOf(ComptesException.class).hasMessageContaining("existe deja");
    }

    @Test
    @DisplayName("Changement de role audite (ancien -> nouveau)")
    void changerRole() {
        AppUser paul = new AppUser("paul", "h", Role.OBSERVATEUR);
        when(utilisateurs.findById(paul.getId())).thenReturn(Optional.of(paul));

        service.changerRole("admin", paul.getId(), Role.OPERATEUR);

        assertThat(paul.getRole()).isEqualTo(Role.OPERATEUR);
        verify(audit).enregistrer("admin", "ROLE_MODIFIE", "paul", "OBSERVATEUR -> OPERATEUR");
    }

    @Test
    @DisplayName("On ne modifie pas son propre role et on ne retire pas le dernier administrateur")
    void protections() {
        AppUser admin = new AppUser("admin", "h", Role.ADMIN);
        AppUser autre = new AppUser("autre", "h", Role.ADMIN);
        when(utilisateurs.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(utilisateurs.findById(autre.getId())).thenReturn(Optional.of(autre));
        when(utilisateurs.findAll()).thenReturn(List.of(admin, autre));

        assertThatThrownBy(() -> service.changerRole("admin", admin.getId(), Role.OBSERVATEUR))
                .isInstanceOf(ComptesException.class).hasMessageContaining("propre role");

        autre.setActif(false);   // admin devient le seul ADMIN actif
        assertThatThrownBy(() -> service.changerRole("autre", admin.getId(), Role.OBSERVATEUR))
                .isInstanceOf(ComptesException.class).hasMessageContaining("au moins un administrateur");
        assertThatThrownBy(() -> service.basculerActif("autre", admin.getId()))
                .isInstanceOf(ComptesException.class).hasMessageContaining("au moins un administrateur");
    }

    @Test
    @DisplayName("Changement de mot de passe : ancien verifie, nouveau different, indicateur remis a faux")
    void changerMotDePasse() {
        AppUser u = new AppUser("paul", encodeur.encode("ancien-mot-de-passe"), Role.OPERATEUR);
        when(utilisateurs.findByNomUtilisateur("paul")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.changerMotDePasse("paul", "faux-faux-faux-faux", "nouveau-mot-de-passe"))
                .isInstanceOf(ComptesException.class).hasMessageContaining("actuel incorrect");
        assertThatThrownBy(() -> service.changerMotDePasse("paul", "ancien-mot-de-passe", "ancien-mot-de-passe"))
                .isInstanceOf(ComptesException.class).hasMessageContaining("different");

        service.changerMotDePasse("paul", "ancien-mot-de-passe", "nouveau-mot-de-passe");

        assertThat(encodeur.matches("nouveau-mot-de-passe", u.getMotDePasseHash())).isTrue();
        assertThat(u.isMotDePasseAChanger()).isFalse();
        verify(audit).enregistrer("paul", "MOT_DE_PASSE_CHANGE", "paul", null);
    }
}

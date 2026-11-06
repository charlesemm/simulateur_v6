package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.persistence.entity.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByNomUtilisateur(String nomUtilisateur);

    List<AppUser> findAllByOrderByNomUtilisateurAsc();
}

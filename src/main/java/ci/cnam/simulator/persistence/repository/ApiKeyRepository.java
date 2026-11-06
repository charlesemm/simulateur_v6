package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.persistence.entity.ApiKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByHash(String hash);
}

package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.competicion.model.MultaConfig;

import java.util.Optional;
import java.util.UUID;

public interface MultaConfigRepository extends JpaRepository<MultaConfig, UUID> {
    Optional<MultaConfig> findByTorneoId(UUID torneoId);
}

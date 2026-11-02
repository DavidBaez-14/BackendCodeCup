package terminus.co.edu.ufps.codecup.analytics.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotGoleador;

import java.util.List;
import java.util.UUID;

public interface SnapshotGoleadorRepository extends JpaRepository<SnapshotGoleador, UUID> {
    List<SnapshotGoleador> findByTorneoIdOrderByGolesDescJugadorNombreAsc(UUID torneoId, Pageable pageable);
    void deleteByTorneoId(UUID torneoId);
}

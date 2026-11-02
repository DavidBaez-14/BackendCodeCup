package terminus.co.edu.ufps.codecup.analytics.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPortero;

import java.util.List;
import java.util.UUID;

public interface SnapshotPorteroRepository extends JpaRepository<SnapshotPortero, UUID> {
    List<SnapshotPortero> findByTorneoIdOrderByPromedioGcAscEquipoNombreAsc(UUID torneoId, Pageable pageable);
    void deleteByTorneoId(UUID torneoId);
}

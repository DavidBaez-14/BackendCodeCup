package terminus.co.edu.ufps.codecup.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPosicion;

import java.util.List;
import java.util.UUID;

public interface SnapshotPosicionRepository extends JpaRepository<SnapshotPosicion, UUID> {
    List<SnapshotPosicion> findByTorneoIdOrderByGrupoAscPosicionAsc(UUID torneoId);
    void deleteByTorneoId(UUID torneoId);
}

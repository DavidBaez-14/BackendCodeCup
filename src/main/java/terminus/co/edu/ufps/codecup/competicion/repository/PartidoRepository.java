package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PartidoRepository extends JpaRepository<Partido, UUID> {
    List<Partido> findByTorneoIdOrderByFechaAsc(UUID torneoId);
    boolean existsByTorneoId(UUID torneoId);
    boolean existsByTorneoIdAndEstado(UUID torneoId, EstadoPartido estado);
    List<Partido> findByTorneoId(UUID torneoId);

    @Query("SELECT p FROM Partido p WHERE p.torneo.id = :torneoId " +
           "AND (p.equipoLocalTorneo.id = :equipoTorneoId OR p.equipoVisitanteTorneo.id = :equipoTorneoId) " +
           "AND p.estado IN :estados")
    List<Partido> findByEquipoTorneoYEstadoIn(
            @Param("torneoId") UUID torneoId,
            @Param("equipoTorneoId") UUID equipoTorneoId,
            @Param("estados") Collection<EstadoPartido> estados);
}

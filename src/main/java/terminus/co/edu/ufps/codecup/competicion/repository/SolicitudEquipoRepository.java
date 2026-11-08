package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.EstadoSolicitud;
import terminus.co.edu.ufps.codecup.competicion.model.SolicitudEquipo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SolicitudEquipoRepository extends JpaRepository<SolicitudEquipo, UUID> {

    List<SolicitudEquipo> findByEquipoTorneoIdInAndEstado(List<UUID> equipoTorneoIds, EstadoSolicitud estado);

    List<SolicitudEquipo> findByEquipoTorneoIdIn(List<UUID> equipoTorneoIds);

    boolean existsByPersonaIdAndTorneoIdAndEstadoIn(UUID personaId, UUID torneoId, Collection<EstadoSolicitud> estados);

    List<SolicitudEquipo> findByPersonaId(UUID personaId);

    boolean existsByPersonaId(UUID personaId);
}

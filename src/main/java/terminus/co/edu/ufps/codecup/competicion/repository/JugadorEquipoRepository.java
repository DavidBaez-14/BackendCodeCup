package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JugadorEquipoRepository extends JpaRepository<JugadorEquipo, UUID> {
    Optional<JugadorEquipo> findByPersonaIdAndTorneoId(UUID personaId, UUID torneoId);
    boolean existsByPersonaIdAndTorneoId(UUID personaId, UUID torneoId);
    List<JugadorEquipo> findByPersonaIdOrderByFechaInicioDesc(UUID personaId);
    List<JugadorEquipo> findByEquipoTorneoIdOrderByFechaInicioAsc(UUID equipoTorneoId);

    boolean existsByPersonaIdAndTorneoIdAndEstado(UUID personaId, UUID torneoId, EstadoMembresia estado);
    boolean existsByEquipoTorneoIdAndNumeroCamisetaAndEstado(UUID equipoTorneoId, Integer numeroCamiseta, EstadoMembresia estado);
    Optional<JugadorEquipo> findByPersonaIdAndEquipoTorneoId(UUID personaId, UUID equipoTorneoId);
}

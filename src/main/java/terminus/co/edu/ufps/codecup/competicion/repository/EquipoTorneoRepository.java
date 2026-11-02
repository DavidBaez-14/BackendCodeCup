package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipoTorneoRepository extends JpaRepository<EquipoTorneo, UUID> {
    List<EquipoTorneo> findByTorneoIdOrderByFechaInscripcionAsc(UUID torneoId);
    List<EquipoTorneo> findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(UUID torneoId, EstadoInscripcion estado);
    List<EquipoTorneo> findByDelegadoCedulaOrderByFechaInscripcionDesc(String delegadoCedula);
    Optional<EquipoTorneo> findByTorneoIdAndDelegadoCedula(UUID torneoId, String delegadoCedula);
    Optional<EquipoTorneo> findByTorneoIdAndEquipoId(UUID torneoId, UUID equipoId);
    boolean existsByEquipoId(UUID equipoId);
}

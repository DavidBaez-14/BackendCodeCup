package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JugadorEquipoRepository extends JpaRepository<JugadorEquipo, UUID> {
    Optional<JugadorEquipo> findByCedulaAndTorneoId(String cedula, UUID torneoId);
    boolean existsByCedulaAndTorneoId(String cedula, UUID torneoId);
    List<JugadorEquipo> findByCedulaOrderByFechaInicioDesc(String cedula);
    List<JugadorEquipo> findByEquipoTorneoIdOrderByFechaInicioAsc(UUID equipoTorneoId);

    boolean existsByCedulaAndTorneoIdAndEstado(String cedula, UUID torneoId, EstadoMembresia estado);
    boolean existsByEquipoTorneoIdAndNumeroCamisetaAndEstado(UUID equipoTorneoId, Integer numeroCamiseta, EstadoMembresia estado);
    Optional<JugadorEquipo> findByCedulaAndEquipoTorneoId(String cedula, UUID equipoTorneoId);
}

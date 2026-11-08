package terminus.co.edu.ufps.codecup.identidad.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoVinculo;
import terminus.co.edu.ufps.codecup.identidad.model.VinculoPersona;

public interface VinculoPersonaRepository extends JpaRepository<VinculoPersona, UUID> {

    Optional<VinculoPersona> findFirstByCuentaIdAndEstado(UUID cuentaId, EstadoVinculo estado);

    List<VinculoPersona> findByEstadoOrderByCreadoEnAsc(EstadoVinculo estado);

    List<VinculoPersona> findByPersonaIdInAndEstadoOrderByCreadoEnAsc(Collection<UUID> personaIds, EstadoVinculo estado);

    List<VinculoPersona> findByPersonaIdAndEstado(UUID personaId, EstadoVinculo estado);
}

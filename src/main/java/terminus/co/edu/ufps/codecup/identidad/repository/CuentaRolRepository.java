package terminus.co.edu.ufps.codecup.identidad.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

public interface CuentaRolRepository extends JpaRepository<CuentaRol, UUID> {

    List<CuentaRol> findByCuentaId(UUID cuentaId);

    List<CuentaRol> findByCuentaIdAndEstado(UUID cuentaId, EstadoRol estado);

    Optional<CuentaRol> findByCuentaIdAndRol(UUID cuentaId, Rol rol);

    boolean existsByCuentaIdAndRol(UUID cuentaId, Rol rol);

    List<CuentaRol> findByEstadoIn(List<EstadoRol> estados);

    long countByRolAndEstado(Rol rol, EstadoRol estado);
}

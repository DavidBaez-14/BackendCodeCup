package terminus.co.edu.ufps.codecup.competicion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.TorneoRol;

public interface TorneoRolRepository extends JpaRepository<TorneoRol, UUID> {

    boolean existsByTorneoIdAndCuentaIdAndRol(UUID torneoId, UUID cuentaId, RolTorneo rol);

    Optional<TorneoRol> findByTorneoIdAndCuentaIdAndRol(UUID torneoId, UUID cuentaId, RolTorneo rol);

    List<TorneoRol> findByTorneoIdOrderByCreadoEnAsc(UUID torneoId);

    List<TorneoRol> findByTorneoIdAndRolOrderByCreadoEnAsc(UUID torneoId, RolTorneo rol);

    long countByTorneoIdAndRol(UUID torneoId, RolTorneo rol);

    boolean existsByCuentaIdAndRol(UUID cuentaId, RolTorneo rol);

    List<TorneoRol> findByCuentaIdAndRol(UUID cuentaId, RolTorneo rol);

    List<TorneoRol> findByCuentaId(UUID cuentaId);

    /** Torneos donde la cuenta tiene ese rol. */
    @Query("SELECT r.torneo.id FROM TorneoRol r WHERE r.cuentaId = :cuentaId AND r.rol = :rol")
    List<UUID> torneosDe(@Param("cuentaId") UUID cuentaId, @Param("rol") RolTorneo rol);
}

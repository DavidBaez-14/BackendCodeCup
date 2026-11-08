package terminus.co.edu.ufps.codecup.finanzas.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import terminus.co.edu.ufps.codecup.finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.codecup.finanzas.model.Multa;

public interface MultaRepository extends JpaRepository<Multa, UUID> {

    List<Multa> findByPersonaId(UUID personaId);

    List<Multa> findByPersonaIdAndEstado(UUID personaId, EstadoMulta estado);

    List<Multa> findByTorneoIdAndEstado(UUID torneoId, EstadoMulta estado);

    List<Multa> findByPartidoId(UUID partidoId);

    List<Multa> findByTorneoId(UUID torneoId);

    long countByPersonaIdAndEstado(UUID personaId, EstadoMulta estado);

    boolean existsByPersonaId(UUID personaId);

    List<Multa> findByPersonaIdAndTorneoIdAndEstado(UUID personaId, UUID torneoId, EstadoMulta estado);

    boolean existsByPersonaIdAndTorneoIdAndEstado(UUID personaId, UUID torneoId, EstadoMulta estado);

    @Query("SELECT m FROM Multa m WHERE m.personaId = :personaId AND m.torneoId = :torneoId " +
           "AND m.tipoSancion IN ('ROJA', 'ROJA_DIRECTA') " +
           "AND (m.partidosSuspensionRestantes IS NULL OR m.partidosSuspensionRestantes > 0) " +
           "AND m.habilitadoManual = false")
    List<Multa> findSuspensionesActivas(@Param("personaId") UUID personaId, @Param("torneoId") UUID torneoId);

    @Query("SELECT COUNT(m) > 0 FROM Multa m WHERE m.personaId = :personaId AND m.torneoId = :torneoId " +
           "AND m.tipoSancion IN ('ROJA', 'ROJA_DIRECTA') " +
           "AND (m.partidosSuspensionRestantes IS NULL OR m.partidosSuspensionRestantes > 0) " +
           "AND m.habilitadoManual = false")
    boolean existsByPersonaIdAndTorneoIdAndTipoSancionAndPartidosSuspensionRestantesGreaterThanAndHabilitadoManualFalse(
            @Param("personaId") UUID personaId, @Param("torneoId") UUID torneoId);

    @Query("SELECT m FROM Multa m WHERE m.equipoTorneoId = :equipoTorneoId " +
           "AND m.tipoSancion IN ('ROJA', 'ROJA_DIRECTA') " +
           "AND (m.partidosSuspensionRestantes IS NULL OR m.partidosSuspensionRestantes > 0) " +
           "AND m.habilitadoManual = false")
    List<Multa> findSuspensionesActivasByEquipoTorneo(@Param("equipoTorneoId") UUID equipoTorneoId);

    @Query("SELECT m.personaId, m.equipoTorneoId, " +
           "CASE WHEN m.tipoSancion IN ('ROJA', 'ROJA_DIRECTA') " +
           "     AND (m.partidosSuspensionRestantes IS NULL OR m.partidosSuspensionRestantes > 0) " +
           "     AND m.habilitadoManual = false THEN true ELSE false END, " +
           "CASE WHEN m.estado = 'PENDIENTE' THEN true ELSE false END " +
           "FROM Multa m WHERE m.equipoTorneoId = :equipoTorneoId AND m.torneoId = :torneoId " +
           "AND ((m.tipoSancion IN ('ROJA', 'ROJA_DIRECTA') " +
           "     AND (m.partidosSuspensionRestantes IS NULL OR m.partidosSuspensionRestantes > 0) " +
           "     AND m.habilitadoManual = false) " +
           "     OR m.estado = 'PENDIENTE')")
    List<Object[]> findJugadoresBloqueados(
            @Param("equipoTorneoId") UUID equipoTorneoId,
            @Param("torneoId") UUID torneoId);
}

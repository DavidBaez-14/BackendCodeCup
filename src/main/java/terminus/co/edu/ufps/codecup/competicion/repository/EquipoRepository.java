package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.Equipo;

import java.util.List;
import java.util.UUID;

public interface EquipoRepository extends JpaRepository<Equipo, UUID> {
    /** Spec 0026: una persona puede tener varios equipos base. */
    List<Equipo> findByDelegadoPersonaIdOrderByCreatedAtDesc(UUID delegadoPersonaId);
    boolean existsByDelegadoPersonaId(UUID delegadoPersonaId);
}

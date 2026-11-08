package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.PartidoJugador;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartidoJugadorRepository extends JpaRepository<PartidoJugador, UUID> {
    List<PartidoJugador> findByPersonaIdAndJugoTrue(UUID personaId);
    boolean existsByPersonaId(UUID personaId);
    Optional<PartidoJugador> findByPartidoIdAndPersonaId(UUID partidoId, UUID personaId);
    List<PartidoJugador> findByPartidoId(UUID partidoId);
}

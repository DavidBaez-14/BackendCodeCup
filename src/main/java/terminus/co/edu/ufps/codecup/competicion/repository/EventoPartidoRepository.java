package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.EventoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;

import java.util.List;
import java.util.UUID;

public interface EventoPartidoRepository extends JpaRepository<EventoPartido, UUID> {
    List<EventoPartido> findByPartidoIdOrderByOrdenAsc(UUID partidoId);
    List<EventoPartido> findByPersonaId(UUID personaId);
    long countByPartidoIdAndEquipoTorneoIdAndTipoEvento(UUID partidoId, UUID equipoTorneoId, TipoEvento tipoEvento);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(MAX(e.orden), 0) FROM EventoPartido e WHERE e.partido.id = :partidoId")
    Integer findMaxOrdenByPartidoId(UUID partidoId);
}

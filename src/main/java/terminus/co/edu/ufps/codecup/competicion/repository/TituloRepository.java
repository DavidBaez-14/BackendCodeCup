package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import terminus.co.edu.ufps.codecup.competicion.model.Titulo;

import java.util.List;
import java.util.UUID;

public interface TituloRepository extends JpaRepository<Titulo, UUID> {

    /**
     * Títulos de un jugador: hay título si la membresía (JugadorEquipo) coincide
     * con el equipo_torneo premiado.
     */
    @Query("SELECT t FROM Titulo t " +
           "JOIN JugadorEquipo je ON je.equipoTorneo.id = t.equipoTorneo.id " +
           "WHERE je.personaId = :personaId")
    List<Titulo> findByPersonaId(UUID personaId);
}

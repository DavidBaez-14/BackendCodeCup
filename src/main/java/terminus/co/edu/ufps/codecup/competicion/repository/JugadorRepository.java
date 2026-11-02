package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.Jugador;

public interface JugadorRepository extends JpaRepository<Jugador, String> {
}

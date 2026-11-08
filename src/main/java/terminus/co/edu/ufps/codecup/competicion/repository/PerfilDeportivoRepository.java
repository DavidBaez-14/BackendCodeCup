package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.competicion.model.PerfilDeportivo;

import java.util.UUID;

public interface PerfilDeportivoRepository extends JpaRepository<PerfilDeportivo, UUID> {
}

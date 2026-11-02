package terminus.co.edu.ufps.codecup.identidad.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;

public interface PersonaRepository extends JpaRepository<Persona, UUID> {

    /** Hasta M2 el número no se repite aunque cambie el tipo: la competición usa solo el número. */
    boolean existsByNumeroDocumento(String numeroDocumento);

    Optional<Persona> findFirstByNumeroDocumento(String numeroDocumento);
}

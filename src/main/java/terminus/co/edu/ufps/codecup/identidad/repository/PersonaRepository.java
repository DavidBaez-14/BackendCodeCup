package terminus.co.edu.ufps.codecup.identidad.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;

public interface PersonaRepository extends JpaRepository<Persona, UUID> {

    /**
     * El número no se repite aunque cambie el tipo (spec 0022): en Colombia la TI y la CC comparten
     * el NUIP. La BD lo refuerza con el índice único parcial personas_numero_documento_uk (V5).
     */
    boolean existsByNumeroDocumento(String numeroDocumento);

    Optional<Persona> findFirstByNumeroDocumento(String numeroDocumento);
}

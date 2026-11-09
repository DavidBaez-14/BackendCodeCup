package terminus.co.edu.ufps.codecup.competicion.dto.arbitro;

import java.time.LocalDateTime;
import java.util.UUID;

/** Un árbitro asignado a un partido (spec 0025). */
public record ArbitroAsignadoDTO(UUID cuentaId, String nombre, LocalDateTime asignadoEn) {
}

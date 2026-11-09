package terminus.co.edu.ufps.codecup.competicion.dto.arbitro;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Cambiar cuándo y dónde se juega un partido (spec 0025). Lo que llega en null no cambia; para
 * borrar el lugar, envía {@code ""}.
 */
@Data
public class ProgramarPartidoRequest {

    private LocalDateTime fecha;

    @Size(max = 120)
    private String lugar;
}

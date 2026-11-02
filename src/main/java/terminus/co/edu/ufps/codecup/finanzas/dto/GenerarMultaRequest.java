package terminus.co.edu.ufps.codecup.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/**
 * Body que MS2 envía cuando cierra un partido con tarjetas (HU29).
 * MS3 aplica la tabla de sanciones del torneo y crea la(s) multa(s).
 */
@Data
public class GenerarMultaRequest {

    @NotNull
    private UUID partidoId;

    @NotNull
    private UUID equipoTorneoId;

    @NotNull
    private UUID torneoId;

    @NotBlank
    private String cedulaJugador;

    @NotBlank
    private String tipoSancion;  // AMARILLA | AZUL | ROJA | ACUMULACION_AMARILLAS | ROJA_DIRECTA
}

package terminus.co.edu.ufps.codecup.finanzas.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobanteDTO {
    private UUID id;
    private String tipo;
    private UUID referenciaId;
    /** Spec 0024: para agrupar la bandeja por torneo. Null si la inscripción o la multa ya no existe. */
    private UUID torneoId;
    private String torneoNombre;
    private UUID equipoTorneoId;
    private String equipoNombre;
    private String urlArchivo;
    /** Nombre de quien lo subió. */
    private String subidoPor;
    private String estado;
    private String motivoRechazo;
    private String aprobadoPor;
    private LocalDateTime fechaSubida;
    private LocalDateTime fechaResolucion;
}

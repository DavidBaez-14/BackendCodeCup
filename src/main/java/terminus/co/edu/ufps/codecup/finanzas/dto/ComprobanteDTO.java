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
    private String urlArchivo;
    private String cedulaUploader;
    private String estado;
    private String motivoRechazo;
    private String aprobadoPor;
    private LocalDateTime fechaSubida;
    private LocalDateTime fechaResolucion;
}

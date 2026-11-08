package terminus.co.edu.ufps.codecup.competicion.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudDTO {
    private UUID id;
    private UUID personaId;
    /** Para el delegado que decide y para quien la pidió. */
    private String numeroDocumento;
    private String nombre;
    private String correo;
    private Integer alturaCm;
    private String piernaHabil;
    private String posicion;
    private UUID equipoTorneoId;
    private UUID equipoId;
    private String equipoNombre;
    private UUID torneoId;
    private String torneoNombre;
    private String estado;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaResolucion;
    private String motivoRechazo;
}

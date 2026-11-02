package terminus.co.edu.ufps.codecup.competicion.dto.delegado;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoDisponibleDTO {
    private UUID id;
    private String nombre;
    private Integer edicion;
    private String estado;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Boolean inscrito;
    private String estadoInscripcion;
    private UUID equipoTorneoId;
    private String motivoExpulsion;
}

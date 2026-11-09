package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartidoAdminDTO {
    private UUID id;
    private LocalDateTime fecha;
    private String estado;
    private UUID localEquipoTorneoId;
    private String localNombre;
    private UUID visitanteEquipoTorneoId;
    private String visitanteNombre;
    private Integer golesLocal;
    private Integer golesVisitante;

    private String fase;
    private Integer jornada;
    private String grupo;
    private UUID torneoId;
    /** Spec 0025: dónde se juega. */
    private String lugar;
    /** Spec 0025: árbitros asignados a este partido. */
    private java.util.List<terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroAsignadoDTO> arbitros;
}

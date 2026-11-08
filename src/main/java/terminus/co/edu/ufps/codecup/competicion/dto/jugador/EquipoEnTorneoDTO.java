package terminus.co.edu.ufps.codecup.competicion.dto.jugador;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipoEnTorneoDTO {
    private UUID equipoTorneoId;
    private UUID equipoId;
    private String equipoNombre;
    private String delegadoNombre;
    private String estadoInscripcion;
    private Integer miembros;
}

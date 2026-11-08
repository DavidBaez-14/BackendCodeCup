package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartidoJugadorDTO {
    private UUID id;
    private UUID personaId;
    /** Para el árbitro y el admin, que verifican la identidad en la cancha. */
    private String numeroDocumento;
    private String jugadorNombre;
    private UUID equipoTorneoId;
    private String equipoNombre;
    private Integer goles;
    private Boolean jugo;
}

package terminus.co.edu.ufps.codecup.analytics.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoleadorDTO {
    private Integer posicion;
    private UUID personaId;
    private String jugadorNombre;
    private String equipoNombre;
    private Integer goles;
}

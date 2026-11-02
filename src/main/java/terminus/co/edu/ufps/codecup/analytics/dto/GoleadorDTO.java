package terminus.co.edu.ufps.codecup.analytics.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoleadorDTO {
    private Integer posicion;
    private String cedula;
    private String jugadorNombre;
    private String equipoNombre;
    private Integer goles;
}

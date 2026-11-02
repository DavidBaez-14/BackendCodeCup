package terminus.co.edu.ufps.codecup.analytics.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PorteroDTO {
    private Integer posicion;
    private UUID equipoTorneoId;
    private String equipoNombre;
    private Integer golesEnContra;
    private Integer partidosJugados;
    private BigDecimal promedio;
}

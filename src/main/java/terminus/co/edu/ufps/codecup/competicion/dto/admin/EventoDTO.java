package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoDTO {
    private UUID id;
    private Integer orden;
    private String cedula;
    private String jugadorNombre;
    private UUID equipoTorneoId;
    private String equipoNombre;
    private String tipoEvento;
    private LocalDateTime createdAt;
}

package terminus.co.edu.ufps.codecup.competicion.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilPartidoDTO {
    private UUID id;
    private LocalDateTime fecha;
    private String equipo;
    private String rival;
    private Integer goles;
    private List<String> tarjetas;
}

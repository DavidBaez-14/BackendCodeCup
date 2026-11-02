package terminus.co.edu.ufps.codecup.competicion.dto;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilEquipoDTO {
    private UUID id;
    private String nombre;
    private String torneo;
    private LocalDate desde;
    private LocalDate hasta;
}

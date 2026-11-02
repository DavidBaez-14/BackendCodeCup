package terminus.co.edu.ufps.codecup.competicion.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipoDTO {
    private UUID id;
    private String nombre;
}

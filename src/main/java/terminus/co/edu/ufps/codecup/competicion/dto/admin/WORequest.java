package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WORequest {
    private UUID ganadorEquipoTorneoId;
    private String motivo;
}

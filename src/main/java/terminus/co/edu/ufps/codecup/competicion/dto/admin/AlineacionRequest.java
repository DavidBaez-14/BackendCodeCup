package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlineacionRequest {
    private UUID personaId;
    private UUID equipoTorneoId;
}

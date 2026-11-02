package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddAllRequest {
    private UUID equipoTorneoId;
}

package terminus.co.edu.ufps.codecup.competicion.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRequestDTO {
    private UUID equipoTorneoId;
}

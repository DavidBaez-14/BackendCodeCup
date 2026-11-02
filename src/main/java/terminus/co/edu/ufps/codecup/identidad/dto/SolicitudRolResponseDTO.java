package terminus.co.edu.ufps.codecup.identidad.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SolicitudRolResponseDTO {

    private String estado;

    private String mensaje;
}

package terminus.co.edu.ufps.codecup.identidad.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CuentaAdminDTO {

    private UUID id;

    private String numeroDocumento;

    private String nombre;

    private String correo;

    private String estado;

    private LocalDateTime fechaCreacion;

    private List<RolEstadoDTO> roles;
}

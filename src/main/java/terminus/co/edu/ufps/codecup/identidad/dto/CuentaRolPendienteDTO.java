package terminus.co.edu.ufps.codecup.identidad.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

@Data
@Builder
public class CuentaRolPendienteDTO {

    private UUID id;

    private UUID cuentaId;

    private String cedula;

    private Rol rol;

    private EstadoRol estado;

    private LocalDateTime fechaSolicitud;

    private String nombre;

    private String correo;

    private String motivoSolicitud;
}

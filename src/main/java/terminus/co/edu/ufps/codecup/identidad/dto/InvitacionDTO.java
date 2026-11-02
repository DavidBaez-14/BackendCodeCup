package terminus.co.edu.ufps.codecup.identidad.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvitacionDTO {

    private UUID id;

    private String correo;

    private String rol;

    private String estado;

    private String token;

    /** Enlace para compartir con la persona invitada. */
    private String enlace;

    /** Correo de quien la creó; null si la creó el sistema. */
    private String creadaPor;

    private LocalDateTime createdAt;

    private LocalDateTime fechaResolucion;
}

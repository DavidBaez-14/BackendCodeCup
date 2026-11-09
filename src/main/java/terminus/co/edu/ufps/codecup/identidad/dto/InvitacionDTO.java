package terminus.co.edu.ufps.codecup.identidad.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvitacionDTO {

    private UUID id;

    /** Null en una invitación por enlace (spec 0024): la acepta cualquier cuenta con el enlace. */
    private String correo;

    private String rol;

    /** Null = a la plataforma; TORNEO = el rol vale solo en {@link #ambitoId} (spec 0024). */
    private String ambito;

    private UUID ambitoId;

    private String ambitoDescripcion;

    private String estado;

    private String token;

    /** Enlace para compartir con la persona invitada. */
    private String enlace;

    /** Correo de quien la creó; null si la creó el sistema. */
    private String creadaPor;

    private LocalDateTime createdAt;

    private LocalDateTime fechaResolucion;
}

package terminus.co.edu.ufps.codecup.identidad.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * Lo que ve quien abre un enlace de invitación antes de aceptarla (spec 0024, pantalla N-4):
 * "Te invitaron a ser árbitro de Copa X". Se puede consultar sin sesión: no trae el token, ni el
 * correo completo, ni datos de otras personas además del nombre de quien invita.
 */
@Data
@Builder
public class InvitacionVistaDTO {

    /** ADMINISTRADOR (co-admin si hay ámbito), ARBITRO o DELEGADO. */
    private String rol;

    /** Null = a la plataforma; TORNEO = un torneo concreto. */
    private String ambito;

    private java.util.UUID ambitoId;

    /** Por ejemplo el nombre del torneo. */
    private String ambitoDescripcion;

    /** PENDIENTE, ACEPTADA o REVOCADA. Solo una PENDIENTE se puede aceptar. */
    private String estado;

    /** Nombre de quien invita; null si la creó el sistema. */
    private String invitadoPor;

    /**
     * Correo al que va dirigida, enmascarado ("a***@correo.com"), para que la persona sepa con qué
     * cuenta entrar. Null si es una invitación por enlace.
     */
    private String paraCorreo;

    private LocalDateTime createdAt;
}

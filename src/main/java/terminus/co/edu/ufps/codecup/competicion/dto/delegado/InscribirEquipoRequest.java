package terminus.co.edu.ufps.codecup.competicion.dto.delegado;

import java.util.UUID;
import lombok.Data;

/**
 * Qué equipo inscribir (spec 0026). Opcional: sin cuerpo se inscribe el único equipo de la persona.
 * {@code nombreEquipo} crea un equipo nuevo y lo inscribe en el mismo paso.
 */
@Data
public class InscribirEquipoRequest {

    /** Uno de sus equipos base. */
    private UUID equipoId;

    /** Un equipo nuevo con este nombre. Tiene prioridad sobre {@code equipoId}. */
    private String nombreEquipo;

    /** Spec 0029: el token del enlace, para inscribirse en un torneo NO_LISTADO. */
    private String enlaceToken;
}

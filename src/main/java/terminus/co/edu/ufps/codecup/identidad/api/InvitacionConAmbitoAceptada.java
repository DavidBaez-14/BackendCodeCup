package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.UUID;
import terminus.co.edu.ufps.codecup.identidad.model.AmbitoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Evento (spec 0024): una cuenta aceptó una invitación con ámbito, por ejemplo co-admin de un
 * torneo. Identidad no conoce la competición; el módulo dueño del ámbito lo escucha con un
 * {@code @EventListener} síncrono, dentro de la misma transacción: si falla, la aceptación se
 * deshace.
 *
 * @param datoExtra lo que el ámbito necesite además del rol (en la 0026, el nombre del equipo del cupo)
 */
public record InvitacionConAmbitoAceptada(UUID invitacionId, AmbitoInvitacion ambito, UUID ambitoId, Rol rol,
                                          UUID cuentaId, UUID personaId, UUID creadaPorCuentaId, String datoExtra) {
}

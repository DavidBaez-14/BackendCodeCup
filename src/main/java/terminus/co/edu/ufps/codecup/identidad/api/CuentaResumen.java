package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.UUID;

/**
 * Una cuenta tal como la ven otros módulos (spec 0024): por ejemplo, la lista de admins y
 * árbitros de un torneo. El correo solo se muestra a quien administra ese torneo, nunca en
 * endpoints públicos.
 */
public record CuentaResumen(UUID cuentaId, UUID personaId, String nombre, String correo, boolean bloqueada) {
}

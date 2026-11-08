package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.UUID;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/**
 * Lo mínimo que otros módulos necesitan de una persona.
 *
 * <p>El documento y el correo nunca se exponen en endpoints públicos: ahí solo van
 * {@code personaId} y {@link #nombreVisible()} (spec 0022, hay menores de edad).
 *
 * @param cuentaId        su cuenta de CodeCup, o null si nunca se registró (miembro sin cuenta)
 * @param nombreVisible   el nombre para endpoints públicos (ver {@link NombreVisible})
 * @param cuentaBloqueada su cuenta está BLOQUEADA: no puede entrar a ningún plantel (spec 0018)
 */
public record PersonaResumen(UUID personaId, TipoDocumento tipoDocumento, String numeroDocumento,
                             String nombreCompleto, String nombreVisible, String correo,
                             UUID cuentaId, boolean cuentaBloqueada) {

    /** La persona tiene una cuenta de CodeCup (puede iniciar sesión). */
    public boolean tieneCuenta() {
        return cuentaId != null;
    }
}

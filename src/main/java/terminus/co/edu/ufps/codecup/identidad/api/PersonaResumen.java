package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.UUID;

/**
 * Lo mínimo que otros módulos necesitan de una persona. Nunca se expone en endpoints públicos.
 *
 * @param tieneCuenta     la persona tiene una cuenta de CodeCup (puede iniciar sesión)
 * @param cuentaBloqueada su cuenta está BLOQUEADA: no puede entrar a ningún plantel (spec 0018)
 */
public record PersonaResumen(UUID personaId, String numeroDocumento, String nombreCompleto, String correo,
                             boolean tieneCuenta, boolean cuentaBloqueada) {
}

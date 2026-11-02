package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.UUID;

/** Lo mínimo que otros módulos necesitan de una persona. Nunca se expone en endpoints públicos. */
public record PersonaResumen(UUID personaId, String numeroDocumento, String nombreCompleto, String correo) {
}

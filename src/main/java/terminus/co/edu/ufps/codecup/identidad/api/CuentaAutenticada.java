package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.List;
import java.util.UUID;

/** Identidad ya resuelta de quien llama, tal como la ve la capa de seguridad. */
public record CuentaAutenticada(
        UUID cuentaId,
        UUID personaId,
        String nombre,

        boolean bloqueada,
        List<String> roles) {
}

package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.UUID;

/**
 * Lo implementa el módulo de la competición (spec 0026). Identidad lo usa para armar
 * {@code GET /api/auth/me} sin depender de la competición.
 */
public interface ResumenSecciones {

    Secciones de(UUID cuentaId, UUID personaId);
}

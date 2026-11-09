package terminus.co.edu.ufps.codecup.competicion.security;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import terminus.co.edu.ufps.codecup.competicion.service.PermisosTorneo;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/**
 * Permisos por torneo para {@code @PreAuthorize} (spec 0024, plan §5.5). Reemplaza a los
 * {@code hasRole('ADMINISTRADOR')}: ningún rol global da acceso a un torneo.
 *
 * <p>Cada método devuelve {@code true} o lanza un {@link ApiException} con un {@code code}
 * estable (por ejemplo 403 {@code NO_ADMIN_DEL_TORNEO}), para que la app muestre el aviso
 * correcto. SpEL relanza la excepción tal cual y la responde el manejador global. Si el recurso
 * no existe, 404. En expresiones con {@code or}, este bean va al final para no cortar las demás.
 */
@Component("authz")
@RequiredArgsConstructor
public class Authz {

    private final PermisosTorneo permisos;

    /** La cuenta completó su registro (404 CUENTA_NO_REGISTRADA si no). */
    public boolean registrado() {
        cuentaActual();
        return true;
    }

    public boolean adminDeTorneo(UUID torneoId) {
        UUID cuenta = cuentaActual();
        permisos.exigirTorneo(torneoId);
        if (!permisos.esAdmin(torneoId, cuenta)) {
            throw noAdmin();
        }
        return true;
    }

    public boolean adminDeInscripcion(UUID equipoTorneoId) {
        cuentaActual();
        return adminDeTorneo(permisos.torneoDeInscripcion(equipoTorneoId));
    }

    public boolean adminDePartido(UUID partidoId) {
        cuentaActual();
        return adminDeTorneo(permisos.torneoDePartido(partidoId));
    }

    public boolean adminDeMulta(UUID multaId) {
        cuentaActual();
        return adminDeTorneo(permisos.torneoDeMulta(multaId));
    }

    public boolean adminDeComprobante(UUID comprobanteId) {
        cuentaActual();
        return adminDeTorneo(permisos.torneoDeComprobante(comprobanteId));
    }

    /**
     * Spec 0025: opera ese partido (admin del torneo, árbitro asignado o árbitro con
     * todos_los_partidos). 403 NO_ARBITRO_DEL_PARTIDO si no.
     */
    public boolean arbitroDePartido(UUID partidoId) {
        UUID cuenta = cuentaActual();
        if (!permisos.puedeArbitrar(partidoId, cuenta)) {
            throw ApiException.forbidden("NO_ARBITRO_DEL_PARTIDO", "No tienes permisos para arbitrar este partido.");
        }
        return true;
    }

    /** Spec 0025: admin o árbitro del torneo. 403 NO_ARBITRO_DEL_TORNEO si no. */
    public boolean staffDeTorneo(UUID torneoId) {
        UUID cuenta = cuentaActual();
        permisos.exigirTorneo(torneoId);
        if (!permisos.esStaff(torneoId, cuenta)) {
            throw noStaff();
        }
        return true;
    }

    public boolean staffDeInscripcion(UUID equipoTorneoId) {
        cuentaActual();
        return staffDeTorneo(permisos.torneoDeInscripcion(equipoTorneoId));
    }

    public boolean staffDeMulta(UUID multaId) {
        cuentaActual();
        return staffDeTorneo(permisos.torneoDeMulta(multaId));
    }

    /** Admin o árbitro de al menos un torneo. */
    public boolean staffDeAlgunTorneo() {
        if (permisos.torneosDondeEsStaff(cuentaActual()).isEmpty()) {
            throw noStaff();
        }
        return true;
    }

    /** Administra al menos un torneo (consultas que no son de un torneo concreto). */
    public boolean adminDeAlgunTorneo() {
        if (permisos.torneosQueAdministra(cuentaActual()).isEmpty()) {
            throw noAdmin();
        }
        return true;
    }

    /** Cuenta de quien hace la petición. */
    public static UUID cuentaActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Sin sesión");
        }
        return JwtClaims.cuentaId(jwt);
    }

    static ApiException noStaff() {
        return ApiException.forbidden("NO_ARBITRO_DEL_TORNEO", "No administras ni arbitras en este torneo.");
    }

    static ApiException noAdmin() {
        return ApiException.forbidden("NO_ADMIN_DEL_TORNEO", "No administras este torneo.");
    }
}

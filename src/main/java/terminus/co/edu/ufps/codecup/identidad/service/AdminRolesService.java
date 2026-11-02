package terminus.co.edu.ufps.codecup.identidad.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.dto.CuentaRolPendienteDTO;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.notificaciones.NotificacionPublisher;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Gestión de roles por parte del admin (portado de MS1). Sin padrón ni Appwrite:
 * los roles viven solo en {@code identidad.cuenta_roles} y el cambio se nota en
 * la siguiente petición, sin emitir tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRolesService {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final RolesCuenta rolesCuenta;
    private final NotificacionPublisher notificacionPublisher;

    @Transactional(readOnly = true)
    public List<CuentaRolPendienteDTO> listarPendientes(EstadoRol filtro) {
        List<EstadoRol> estados;
        if (filtro == null) {
            estados = List.of(EstadoRol.PENDIENTE, EstadoRol.PENDIENTE_VALIDACION);
        } else if (filtro == EstadoRol.PENDIENTE || filtro == EstadoRol.PENDIENTE_VALIDACION) {
            estados = List.of(filtro);
        } else {
            throw ApiException.badRequest("FILTRO_INVALIDO", "Filtro de estado inválido.");
        }
        return cuentaRolRepository.findByEstadoIn(estados).stream().map(this::toPendienteDTO).toList();
    }

    @Transactional
    public void aprobar(UUID cuentaRolId) {
        var cr = cuentaRolRepository.findById(cuentaRolId)
                .orElseThrow(() -> ApiException.notFound("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        if (cr.getEstado() == EstadoRol.APROBADO) {
            return;
        }
        if (cr.getEstado() == EstadoRol.RECHAZADO) {
            throw ApiException.badRequest("SOLICITUD_RESUELTA", "La solicitud ya fue rechazada.");
        }
        rolesCuenta.otorgar(cr.getCuenta(), cr.getRol(), cr.getMotivoSolicitud());
    }

    /** Aprueba en bloque las solicitudes pendientes de una cuenta. */
    @Transactional
    public int aprobarTodos(UUID cuentaId) {
        var pendientes = pendientesDe(cuentaId);
        for (var cr : pendientes) {
            rolesCuenta.otorgar(cr.getCuenta(), cr.getRol(), cr.getMotivoSolicitud());
        }
        return pendientes.size();
    }

    @Transactional
    public void rechazar(UUID cuentaRolId, String motivo) {
        var cr = cuentaRolRepository.findById(cuentaRolId)
                .orElseThrow(() -> ApiException.notFound("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        if (cr.getEstado() != EstadoRol.PENDIENTE && cr.getEstado() != EstadoRol.PENDIENTE_VALIDACION) {
            throw ApiException.badRequest("SOLICITUD_RESUELTA", "La solicitud ya fue resuelta.");
        }
        rechazarInterno(cr, motivo);
    }

    @Transactional
    public int rechazarTodos(UUID cuentaId, String motivo) {
        var pendientes = pendientesDe(cuentaId);
        for (var cr : pendientes) {
            rechazarInterno(cr, motivo);
        }
        return pendientes.size();
    }

    /**
     * Borra solo la solicitud rechazada. La cuenta conserva sus otros roles (al
     * menos JUGADOR, que es automático), así que ya no se borra la cuenta entera.
     */
    private void rechazarInterno(CuentaRol cr, String motivo) {
        var cuenta = cr.getCuenta();
        String rol = cr.getRol().name();
        cuentaRolRepository.delete(cr);
        log.info("[AUDIT][SOLICITUD_RECHAZADA] cuenta={} rol={} motivo={}", cuenta.getId(), rol, motivo);
        notificacionPublisher.notificarRechazoSolicitud(cuenta.getId(), cuenta.getCorreo(), rol, motivo);
    }

    @Transactional
    public void asignarRol(String cedula, Rol rol, String motivo, UUID callerCuentaId) {
        if (rol == Rol.JUGADOR) {
            throw ApiException.badRequest("ROL_AUTOMATICO", "El rol JUGADOR se asigna solo al registrarse.");
        }
        var cuenta = cuentaPorCedula(cedula);
        var existente = cuentaRolRepository.findByCuentaIdAndRol(cuenta.getId(), rol);
        if (existente.isPresent() && existente.get().getEstado() == EstadoRol.APROBADO) {
            throw ApiException.conflict("ROL_YA_ASIGNADO", "La cuenta ya tiene el rol " + rol.name() + " aprobado.");
        }
        rolesCuenta.otorgar(cuenta, rol, motivo);

        log.info("[AUDIT][ROL_ASIGNADO] caller={} cuenta={} rol={} motivo={}", callerCuentaId, cuenta.getId(), rol, motivo);
        notificacionPublisher.notificarAsignacionRol(cuenta.getId(), cuenta.getCorreo(), rol.name());
    }

    @Transactional
    public void revocarRol(String cedula, Rol rol, String motivo, UUID callerCuentaId) {
        var cuenta = cuentaPorCedula(cedula);
        var cr = cuentaRolRepository.findByCuentaIdAndRol(cuenta.getId(), rol)
                .filter(x -> x.getEstado() == EstadoRol.APROBADO)
                .orElseThrow(() -> ApiException.notFound("ROL_NO_ASIGNADO",
                        "La cuenta no tiene el rol " + rol.name() + " aprobado."));

        if (rol == Rol.ADMINISTRADOR) {
            if (cuenta.getId().equals(callerCuentaId)) {
                throw ApiException.badRequest("AUTO_REVOCACION",
                        "No puedes revocarte tu propio rol de administrador. Pide a otro administrador que lo haga.");
            }
            exigirOtroAdmin("No se puede revocar al único administrador del sistema.");
        }

        cuentaRolRepository.delete(cr);
        log.info("[AUDIT][ROL_REVOCADO] caller={} cuenta={} rol={} motivo={}", callerCuentaId, cuenta.getId(), rol, motivo);
        notificacionPublisher.notificarRevocacionRol(cuenta.getId(), cuenta.getCorreo(), rol.name(), motivo);
    }

    /**
     * "Eliminar cuenta" = bloquearla y quitarle los roles. El usuario sigue
     * existiendo en Supabase (el backend no llama al proveedor), pero cualquier
     * petición suya responde 403 CUENTA_BLOQUEADA.
     */
    @Transactional
    public void eliminarCuenta(String cedula, String motivo, UUID callerCuentaId) {
        var cuenta = cuentaPorCedula(cedula);
        if (cuenta.getId().equals(callerCuentaId)) {
            throw ApiException.badRequest("AUTO_BLOQUEO",
                    "No puedes eliminar tu propia cuenta. Pide a otro administrador que lo haga.");
        }
        boolean esAdmin = cuentaRolRepository.findByCuentaIdAndRol(cuenta.getId(), Rol.ADMINISTRADOR)
                .filter(cr -> cr.getEstado() == EstadoRol.APROBADO)
                .isPresent();
        if (esAdmin) {
            exigirOtroAdmin("No se puede eliminar al único administrador del sistema.");
        }

        cuentaRolRepository.deleteAll(cuentaRolRepository.findByCuentaId(cuenta.getId()));
        cuenta.setEstado(EstadoCuenta.BLOQUEADA);
        cuentaRepository.save(cuenta);

        log.info("[AUDIT][CUENTA_BLOQUEADA] caller={} cuenta={} motivo={}", callerCuentaId, cuenta.getId(), motivo);
        notificacionPublisher.notificarBloqueoCuenta(cuenta.getId(), cuenta.getCorreo(), motivo);
    }

    private List<CuentaRol> pendientesDe(UUID cuentaId) {
        var cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA", "Cuenta no encontrada."));
        var pendientes = cuentaRolRepository.findByCuentaId(cuenta.getId()).stream()
                .filter(cr -> cr.getEstado() == EstadoRol.PENDIENTE || cr.getEstado() == EstadoRol.PENDIENTE_VALIDACION)
                .toList();
        if (pendientes.isEmpty()) {
            throw ApiException.badRequest("SIN_PENDIENTES", "La cuenta no tiene solicitudes pendientes.");
        }
        return pendientes;
    }

    private void exigirOtroAdmin(String mensaje) {
        if (cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO) <= 1) {
            throw ApiException.conflict("ULTIMO_ADMIN", mensaje);
        }
    }

    private Cuenta cuentaPorCedula(String cedula) {
        return cuentaRepository.findByNumeroDocumento(Documentos.normalizar(cedula))
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA",
                        "No existe una cuenta registrada con esa cédula."));
    }

    private CuentaRolPendienteDTO toPendienteDTO(CuentaRol cr) {
        var cuenta = cr.getCuenta();
        var persona = cuenta.getPersona();
        return CuentaRolPendienteDTO.builder()
                .id(cr.getId())
                .cuentaId(cuenta.getId())
                .cedula(persona != null ? persona.getNumeroDocumento() : null)
                .rol(cr.getRol())
                .estado(cr.getEstado())
                .fechaSolicitud(cr.getFechaSolicitud())
                .nombre(persona != null ? persona.nombreCompleto() : null)
                .correo(cuenta.getCorreo())
                .motivoSolicitud(cr.getMotivoSolicitud())
                .build();
    }
}

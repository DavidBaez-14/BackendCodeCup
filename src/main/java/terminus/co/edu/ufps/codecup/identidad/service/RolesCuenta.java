package terminus.co.edu.ufps.codecup.identidad.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;

/** Reglas compartidas para otorgar roles (registro, invitación, asignación y bootstrap). */
@Service
@RequiredArgsConstructor
public class RolesCuenta {

    private final CuentaRolRepository cuentaRolRepository;

    /**
     * Deja el rol aprobado, sea nuevo o una solicitud previa. Idempotente.
     * Regla heredada de MS1: todo ADMINISTRADOR también es DELEGADO (y JUGADOR,
     * que ya tiene desde el registro). Desde la spec 0024, ADMINISTRADOR y SUPERADMIN
     * van juntos: la web muestra el panel de Usuarios con ADMINISTRADOR y el backend lo
     * autoriza con SUPERADMIN.
     */
    @Transactional
    public void otorgar(Cuenta cuenta, Rol rol, String motivo) {
        aprobar(cuenta, rol, motivo);
        if (rol == Rol.ADMINISTRADOR || rol == Rol.SUPERADMIN) {
            aprobar(cuenta, Rol.ADMINISTRADOR, motivo);
            aprobar(cuenta, Rol.SUPERADMIN, motivo);
            aprobar(cuenta, Rol.DELEGADO, motivo);
        }
    }

    public List<String> aprobados(Cuenta cuenta) {
        return nombres(cuenta, EstadoRol.APROBADO);
    }

    public List<String> pendientes(Cuenta cuenta) {
        return cuentaRolRepository.findByCuentaId(cuenta.getId()).stream()
                .filter(cr -> cr.getEstado() == EstadoRol.PENDIENTE || cr.getEstado() == EstadoRol.PENDIENTE_VALIDACION)
                .map(cr -> cr.getRol().name())
                .sorted()
                .toList();
    }

    private List<String> nombres(Cuenta cuenta, EstadoRol estado) {
        return cuentaRolRepository.findByCuentaIdAndEstado(cuenta.getId(), estado).stream()
                .map(cr -> cr.getRol().name())
                .sorted()
                .toList();
    }

    private void aprobar(Cuenta cuenta, Rol rol, String motivo) {
        var ahora = LocalDateTime.now();
        var existente = cuentaRolRepository.findByCuentaIdAndRol(cuenta.getId(), rol);
        if (existente.isPresent()) {
            var cr = existente.get();
            if (cr.getEstado() == EstadoRol.APROBADO) return;
            cr.setEstado(EstadoRol.APROBADO);
            cr.setFechaResolucion(ahora);
            cr.setMotivoRechazo(null);
            cuentaRolRepository.save(cr);
            return;
        }
        cuentaRolRepository.save(CuentaRol.builder()
                .cuenta(cuenta)
                .rol(rol)
                .estado(EstadoRol.APROBADO)
                .fechaSolicitud(ahora)
                .fechaResolucion(ahora)
                .motivoSolicitud(motivo)
                .build());
    }
}

package terminus.co.edu.ufps.codecup.identidad.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.InvitacionRepository;
import terminus.co.edu.ufps.codecup.identidad.service.InvitacionService;
import terminus.co.edu.ufps.codecup.identidad.service.RolesCuenta;

/**
 * Primer administrador sin credenciales en el código (reemplaza a StartupRunner de MS1).
 * Si {@code BOOTSTRAP_ADMIN_EMAIL} está configurado y no hay ningún admin aprobado:
 * <ul>
 *   <li>si ya existe una cuenta con ese correo, recibe ADMINISTRADOR;</li>
 *   <li>si no, se crea una invitación de ADMINISTRADOR que se aplica cuando se registre.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final InvitacionRepository invitacionRepository;
    private final InvitacionService invitacionService;
    private final RolesCuenta rolesCuenta;

    @Value("${app.bootstrap-admin-email:}")
    private String bootstrapAdminEmail;

    @Override
    public void run(ApplicationArguments args) {
        ejecutar(bootstrapAdminEmail);
    }

    public void ejecutar(String email) {
        String correo = Documentos.normalizarCorreo(email);
        if (correo == null) return;
        if (cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO) > 0) return;

        var cuenta = cuentaRepository.findFirstByCorreo(correo);
        if (cuenta.isPresent()) {
            if (cuenta.get().getEstado() == EstadoCuenta.BLOQUEADA) {
                log.warn("[BOOTSTRAP] La cuenta de BOOTSTRAP_ADMIN_EMAIL está bloqueada; no se asigna ADMINISTRADOR.");
                return;
            }
            rolesCuenta.otorgar(cuenta.get(), Rol.ADMINISTRADOR, "Bootstrap");
            log.info("[BOOTSTRAP] ADMINISTRADOR asignado a la cuenta {}", cuenta.get().getId());
            return;
        }
        if (!invitacionRepository.existsByCorreoAndRolAndEstadoAndAmbitoIsNull(correo, Rol.ADMINISTRADOR, EstadoInvitacion.PENDIENTE)) {
            invitacionService.crearDelSistema(correo, Rol.ADMINISTRADOR);
            log.info("[BOOTSTRAP] Invitación de ADMINISTRADOR creada; se aplica cuando ese correo complete el registro.");
        }
    }
}

package terminus.co.edu.ufps.codecup.identidad.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.InvitacionRepository;
import terminus.co.edu.ufps.codecup.identidad.service.InvitacionService;
import terminus.co.edu.ufps.codecup.identidad.service.RolesCuenta;

/** Spec 0017, criterio 15: primer admin sin credenciales en el código. */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminTest {

    @Mock CuentaRepository cuentaRepository;
    @Mock CuentaRolRepository cuentaRolRepository;
    @Mock InvitacionRepository invitacionRepository;
    @Mock InvitacionService invitacionService;
    @Mock RolesCuenta rolesCuenta;
    @InjectMocks BootstrapAdmin bootstrap;

    @Test
    void creaInvitacionSiNoHayAdminNiCuenta() {
        when(cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO)).thenReturn(0L);
        when(cuentaRepository.findFirstByCorreo("admin@ufps.edu.co")).thenReturn(Optional.empty());
        when(invitacionRepository.existsByCorreoAndRolAndEstado("admin@ufps.edu.co", Rol.ADMINISTRADOR,
                EstadoInvitacion.PENDIENTE)).thenReturn(false);

        bootstrap.ejecutar("  Admin@UFPS.edu.co ");

        verify(invitacionService).crearDelSistema("admin@ufps.edu.co", Rol.ADMINISTRADOR);
    }

    @Test
    void asignaDirectoSiLaCuentaYaExiste() {
        var cuenta = Cuenta.builder().id(UUID.randomUUID()).correo("admin@ufps.edu.co")
                .estado(EstadoCuenta.ACTIVA).fechaCreacion(LocalDateTime.now()).build();
        when(cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO)).thenReturn(0L);
        when(cuentaRepository.findFirstByCorreo("admin@ufps.edu.co")).thenReturn(Optional.of(cuenta));

        bootstrap.ejecutar("admin@ufps.edu.co");

        verify(rolesCuenta).otorgar(cuenta, Rol.ADMINISTRADOR, "Bootstrap");
        verify(invitacionService, never()).crearDelSistema(anyString(), any());
    }

    @Test
    void noHaceNadaSiYaHayAdmin() {
        when(cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO)).thenReturn(1L);

        bootstrap.ejecutar("admin@ufps.edu.co");

        verifyNoInteractions(invitacionService, rolesCuenta, cuentaRepository);
    }

    @Test
    void sinCorreoConfigurado_noHaceNada() {
        bootstrap.ejecutar("");

        verifyNoInteractions(cuentaRolRepository, invitacionService, rolesCuenta, cuentaRepository);
    }
}

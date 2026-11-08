package terminus.co.edu.ufps.codecup.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.notificaciones.NotificacionPublisher;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/** Spec 0017, criterios 11 y 12: eliminar = bloquear; rechazar solo borra la solicitud. */
@ExtendWith(MockitoExtension.class)
class AdminRolesServiceTest {

    @Mock CuentaRepository cuentaRepository;
    @Mock CuentaRolRepository cuentaRolRepository;
    @Mock RolesCuenta rolesCuenta;
    @Mock NotificacionPublisher notificacionPublisher;
    @InjectMocks AdminRolesService service;

    private static Cuenta cuenta(String cedula) {
        return Cuenta.builder()
                .id(UUID.randomUUID())
                .proveedor(Cuenta.PROVEEDOR_SUPABASE).proveedorSub(UUID.randomUUID().toString())
                .correo("x@correo.test")
                .persona(Persona.builder().id(UUID.randomUUID()).nombres("Ana").numeroDocumento(cedula).build())
                .estado(EstadoCuenta.ACTIVA)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private static CuentaRol rol(Cuenta c, Rol rol, EstadoRol estado) {
        return CuentaRol.builder().id(UUID.randomUUID()).cuenta(c).rol(rol).estado(estado)
                .fechaSolicitud(LocalDateTime.now()).build();
    }

    @Test
    void eliminarCuenta_bloqueaYBorraRoles() {
        var c = cuenta("1090");
        var roles = List.of(rol(c, Rol.JUGADOR, EstadoRol.APROBADO), rol(c, Rol.DELEGADO, EstadoRol.APROBADO));
        when(cuentaRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cuentaRolRepository.findByCuentaIdAndRol(c.getId(), Rol.ADMINISTRADOR)).thenReturn(Optional.empty());
        when(cuentaRolRepository.findByCuentaId(c.getId())).thenReturn(roles);

        service.eliminarCuenta(c.getId(), "Suplantación", UUID.randomUUID());

        assertThat(c.getEstado()).isEqualTo(EstadoCuenta.BLOQUEADA);
        verify(cuentaRolRepository).deleteAll(roles);
        verify(cuentaRepository).save(c);
        verify(cuentaRepository, never()).delete(any());
    }

    @Test
    void noPuedeBloquearseASiMismo() {
        var c = cuenta("1090");
        when(cuentaRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.eliminarCuenta(c.getId(), "x", c.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "AUTO_BLOQUEO");
        assertThat(c.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    void noSeBloqueaAlUltimoAdmin() {
        var c = cuenta("1090");
        when(cuentaRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cuentaRolRepository.findByCuentaIdAndRol(c.getId(), Rol.ADMINISTRADOR))
                .thenReturn(Optional.of(rol(c, Rol.ADMINISTRADOR, EstadoRol.APROBADO)));
        when(cuentaRolRepository.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoRol.APROBADO)).thenReturn(1L);

        assertThatThrownBy(() -> service.eliminarCuenta(c.getId(), "x", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "ULTIMO_ADMIN");
    }

    @Test
    void rechazar_soloBorraLaSolicitud() {
        var c = cuenta("1090");
        var solicitud = rol(c, Rol.DELEGADO, EstadoRol.PENDIENTE);
        when(cuentaRolRepository.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));

        service.rechazar(solicitud.getId(), "Sin equipo");

        verify(cuentaRolRepository).delete(solicitud);
        verify(cuentaRepository, never()).delete(any());
        verify(cuentaRolRepository, never()).deleteAll(any());
        assertThat(c.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    void asignarJugador_400PorqueEsAutomatico() {
        assertThatThrownBy(() -> service.asignarRol(UUID.randomUUID(), Rol.JUGADOR, "x", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "ROL_AUTOMATICO");
    }
}

package terminus.co.edu.ufps.codecup.notificaciones;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LoggingNotificacionPublisher implements NotificacionPublisher {

    @Override
    public void notificarRojaSuspendida(UUID personaId, UUID partidoId, int fechasSuspension) {
        log.info("[NOTIF] roja: persona={} partido={} partidos={}", personaId, partidoId, fechasSuspension);
    }

    @Override
    public void notificarAsignacionRol(UUID cuentaId, String correo, String rol) {
        log.info("[NOTIF] rol asignado: cuenta={} rol={}", cuentaId, rol);
    }

    @Override
    public void notificarRevocacionRol(UUID cuentaId, String correo, String rol, String motivo) {
        log.info("[NOTIF] rol revocado: cuenta={} rol={} motivo={}", cuentaId, rol, motivo);
    }

    @Override
    public void notificarRechazoSolicitud(UUID cuentaId, String correo, String rol, String motivo) {
        log.info("[NOTIF] solicitud rechazada: cuenta={} rol={} motivo={}", cuentaId, rol, motivo);
    }

    @Override
    public void notificarBloqueoCuenta(UUID cuentaId, String correo, String motivo) {
        log.info("[NOTIF] cuenta bloqueada: cuenta={} motivo={}", cuentaId, motivo);
    }
}

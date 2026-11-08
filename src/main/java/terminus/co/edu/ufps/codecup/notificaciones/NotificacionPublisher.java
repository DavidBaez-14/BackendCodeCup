package terminus.co.edu.ufps.codecup.notificaciones;

import java.util.UUID;

/**
 * Único punto de salida de notificaciones del backend. Hoy solo escribe logs
 * ({@link LoggingNotificacionPublisher}); el envío por correo (Brevo) vendrá después.
 */
public interface NotificacionPublisher {

    void notificarRojaSuspendida(UUID personaId, UUID partidoId, int fechasSuspension);

    void notificarAsignacionRol(UUID cuentaId, String correo, String rol);

    void notificarRevocacionRol(UUID cuentaId, String correo, String rol, String motivo);

    void notificarRechazoSolicitud(UUID cuentaId, String correo, String rol, String motivo);

    void notificarBloqueoCuenta(UUID cuentaId, String correo, String motivo);
}

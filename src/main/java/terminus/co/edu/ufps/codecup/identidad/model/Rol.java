package terminus.co.edu.ufps.codecup.identidad.model;

/**
 * Roles globales de la cuenta. JUGADOR se asigna solo al registrarse.
 *
 * <p>Desde la spec 0024 los permisos sobre un torneo viven en {@code competicion.torneo_roles}:
 * ningún rol global da acceso a un torneo. {@link #SUPERADMIN} solo abre el panel de Usuarios
 * (buscar, bloquear e invitar a la plataforma). {@link #ADMINISTRADOR} queda por compatibilidad
 * con la web: se otorga y se revoca junto con SUPERADMIN.
 */
public enum Rol {
    SUPERADMIN,
    ADMINISTRADOR,
    ARBITRO,
    DELEGADO,
    JUGADOR
}

package terminus.co.edu.ufps.codecup.identidad.model;

public enum EstadoRol {
    PENDIENTE,
    /** Legado del padrón: ya no se produce. Se conserva para el filtro del panel de admin. */
    PENDIENTE_VALIDACION,
    APROBADO,
    RECHAZADO
}

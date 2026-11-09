package terminus.co.edu.ufps.codecup.competicion.model;

/**
 * Quién puede ver un torneo (spec 0029, plan §6). La regla vive en {@code VisibilidadService}.
 */
public enum Visibilidad {
    /** Aparece en Explorar y se ve sin cuenta. */
    PUBLICO,
    /** Solo con el enlace; nunca aparece en listas. */
    NO_LISTADO,
    /** Solo lo ven sus participantes con sesión. */
    PRIVADO
}

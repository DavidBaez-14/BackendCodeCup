package terminus.co.edu.ufps.codecup.identidad.model;

/** Eliminar una cuenta la bloquea: el backend nunca borra usuarios del proveedor de auth. */
public enum EstadoCuenta {
    ACTIVA,
    BLOQUEADA
}

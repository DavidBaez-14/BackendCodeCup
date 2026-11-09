package terminus.co.edu.ufps.codecup.competicion.model;

/**
 * Rol de una cuenta en un torneo concreto (spec 0024). No hay administradores globales: quien
 * crea un torneo queda como su ADMIN y puede invitar co-admins y árbitros.
 *
 * <p>Spec 0025: un ARBITRO del torneo opera los partidos que le asignan, o todos si tiene
 * {@code todos_los_partidos}.
 */
public enum RolTorneo {
    ADMIN,
    ARBITRO
}

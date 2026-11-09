package terminus.co.edu.ufps.codecup.identidad.model;

/**
 * Ámbito de una invitación (spec 0024). Sin ámbito (null), la invitación es a la plataforma y
 * otorga un rol global. Con ámbito TORNEO, el rol vale solo en ese torneo ({@code ambito_id}).
 */
public enum AmbitoInvitacion {
    TORNEO
}

package terminus.co.edu.ufps.codecup.competicion.dto.creador;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;

/** Invitar a alguien a un torneo como co-admin o árbitro (spec 0024 y 0025). */
@Data
public class InvitarATorneoRequest {

    /** ADMIN (co-admin) o ARBITRO. */
    @NotNull
    private RolTorneo rol;

    /** Opcional: sin correo la invitación es "por enlace" y la acepta la primera cuenta que la abra. */
    @Email
    @Size(max = 150)
    private String correo;
}

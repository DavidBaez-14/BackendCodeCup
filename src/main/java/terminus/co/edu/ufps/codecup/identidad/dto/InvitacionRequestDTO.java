package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

@Data
public class InvitacionRequestDTO {

    @NotBlank
    @Email
    @Size(max = 150)
    private String correo;

    /** ADMINISTRADOR, ARBITRO o DELEGADO. JUGADOR no se invita: es automático. */
    @NotNull
    private Rol rol;
}

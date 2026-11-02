package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

@Data
public class SolicitarRolRequestDTO {

    @NotNull
    private Rol rol;

    @Size(max = 500)
    private String motivoSolicitud;
}

package terminus.co.edu.ufps.codecup.identidad.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

@Data
public class AsignarRolRequestDTO {

    @NotNull
    private UUID cuentaId;

    @NotNull
    private Rol rol;

    @NotNull
    private String motivo;
}

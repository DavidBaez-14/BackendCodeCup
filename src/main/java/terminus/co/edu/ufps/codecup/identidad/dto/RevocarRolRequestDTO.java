package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

@Data
public class RevocarRolRequestDTO {

    @NotBlank
    private String cedula;

    @NotNull
    private Rol rol;

    @NotBlank
    private String motivo;
}

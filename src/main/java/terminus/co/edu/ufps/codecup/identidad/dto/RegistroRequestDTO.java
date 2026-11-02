package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

@Data
public class RegistroRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String nombres;

    @NotBlank
    @Size(max = 100)
    private String apellidos;

    /** Obligatorio solo hasta M2, mientras la competición use la cédula como llave. */
    @NotNull
    private TipoDocumento tipoDocumento;

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9. ]+$", message = "solo letras, números, puntos y espacios")
    private String numeroDocumento;
}

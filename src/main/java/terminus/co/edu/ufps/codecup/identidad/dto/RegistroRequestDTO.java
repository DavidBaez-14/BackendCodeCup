package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.NotBlank;

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

    /** Opcional (spec 0023): si llega, va con el número. */
    private TipoDocumento tipoDocumento;

    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9. ]+$", message = "solo letras, números, puntos y espacios")
    private String numeroDocumento;
}

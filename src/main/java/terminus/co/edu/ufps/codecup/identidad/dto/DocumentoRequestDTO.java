package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/** Agregar el documento después del registro (spec 0023). */
@Data
public class DocumentoRequestDTO {

    @NotNull
    private TipoDocumento tipoDocumento;

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9. ]+$", message = "solo letras, números, puntos y espacios")
    private String numeroDocumento;
}

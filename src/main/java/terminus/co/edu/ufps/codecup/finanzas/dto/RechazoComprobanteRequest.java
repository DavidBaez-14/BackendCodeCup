package terminus.co.edu.ufps.codecup.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RechazoComprobanteRequest {

    @NotBlank
    private String motivo;
}

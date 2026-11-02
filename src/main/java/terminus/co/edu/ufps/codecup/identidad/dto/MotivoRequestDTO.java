package terminus.co.edu.ufps.codecup.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Motivo de un rechazo o de un bloqueo de cuenta. */
@Data
public class MotivoRequestDTO {

    @NotBlank
    private String motivo;
}

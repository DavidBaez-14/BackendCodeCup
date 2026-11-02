package terminus.co.edu.ufps.codecup.finanzas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RegistrarPagoTodasRequest {
    @NotNull
    private UUID torneoId;

    @NotNull
    private UUID partidoPagoId;
}

package terminus.co.edu.ufps.codecup.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class SubirComprobanteRequest {

    @NotNull
    private String tipo;          // INSCRIPCION | MULTA

    @NotNull
    private UUID referenciaId;    // equipo_torneo_id o multa_id según tipo

    @NotBlank
    private String urlArchivo;    // URL devuelta por Supabase Storage tras el upload
}

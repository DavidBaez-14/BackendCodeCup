package terminus.co.edu.ufps.codecup.competicion.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Un miembro sin cuenta del plantel, tal como lo ve quien busca "ese soy yo" (spec 0023, D3).
 * Nunca lleva documento ni correo.
 */
@Data
@Builder
public class MiembroReclamableDTO {
    private UUID personaId;
    private String nombre;
    private Integer numeroCamiseta;
}

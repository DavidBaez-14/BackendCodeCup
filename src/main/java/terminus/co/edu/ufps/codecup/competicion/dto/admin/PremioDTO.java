package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremioDTO {
    private UUID id;
    private UUID premioId;
    private String codigoCatalogo;
    private String nombreCatalogo;
    private String titulo;
    private String descripcion;
    private Integer monto;
    private UUID ganadorPersonaId;
    private String ganadorNombre;
    private UUID ganadorEquipoTorneoId;
    private String ganadorEquipoNombre;
    private LocalDateTime asignadoEn;
}

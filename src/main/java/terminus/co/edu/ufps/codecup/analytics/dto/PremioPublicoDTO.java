package terminus.co.edu.ufps.codecup.analytics.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Proyección de lectura de un premio para el consumo público de analytics.
 * No confundir con {@code competicion.dto.admin.PremioDTO}, que es el DTO
 * de administración del que se deriva esta proyección.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremioPublicoDTO {
    private String categoria;
    private String alcance;
    private String nombre;
    private Integer monto;
    private Integer orden;
    private GanadorDTO ganador;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GanadorDTO {
        private UUID personaId;
        private String nombre;
        private String equipoNombre;
    }
}

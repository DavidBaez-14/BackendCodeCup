package terminus.co.edu.ufps.codecup.competicion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilResumenDTO {
    private int partidosJugados;
    private int goles;
    private PerfilTarjetasDTO tarjetas;
    private int titulos;
}

package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanzasConfigDTO {
    private InscripcionConfigDTO inscripcion;
    private MultaConfigDTO multas;
    private Long premiosCount;
}

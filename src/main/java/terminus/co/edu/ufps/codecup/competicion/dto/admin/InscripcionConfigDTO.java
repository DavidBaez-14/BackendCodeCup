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
public class InscripcionConfigDTO {
    private BigDecimal monto;
    private String cuentaDestino;
    private String bancoDestino;
    private String titularCuenta;
}

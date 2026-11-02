package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscripcionInfoDTO {
    private UUID equipoTorneoId;
    private BigDecimal montoInscripcion;
    private String cuentaDestino;
    private String bancoDestino;
    private String titularCuenta;
    private String estadoComprobante;
}

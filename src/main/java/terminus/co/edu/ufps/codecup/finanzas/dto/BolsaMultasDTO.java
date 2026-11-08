package terminus.co.edu.ufps.codecup.finanzas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BolsaMultasDTO {
    private BigDecimal totalPagado;
    private BigDecimal totalPendiente;
    private List<DesglosePartido> desglosePorPartido;
    private List<DesgloseArbitro> desglosePorArbitro;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DesglosePartido {
        private UUID partidoPagoId;
        private BigDecimal total;
        private Long cantidad;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DesgloseArbitro {
        private UUID pagadoPorCuentaId;
        private String pagadoPor;
        private BigDecimal total;
        private Long cantidad;
    }
}

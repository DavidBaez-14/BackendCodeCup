package terminus.co.edu.ufps.codecup.finanzas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPagoResult {
    private BigDecimal totalPagado;
    private List<UUID> multasPagadasIds;
}

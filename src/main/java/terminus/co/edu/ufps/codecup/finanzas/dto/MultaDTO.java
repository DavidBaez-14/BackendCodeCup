package terminus.co.edu.ufps.codecup.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultaDTO {
    private UUID id;
    private String cedula;
    private UUID partidoId;
    private UUID equipoTorneoId;
    private UUID torneoId;
    private String tipoSancion;
    private BigDecimal monto;
    private String estado;
    private Integer partidosSuspension;
    private Integer partidosSuspensionRestantes;
    private String pagadoPorCedula;
    private UUID partidoPagoId;
    private Boolean habilitadoManual;
    private String habilitadoPorCedula;
    private LocalDateTime habilitadoEn;
    private String motivoHabilitacion;
    private LocalDateTime fechaGeneracion;
    private LocalDateTime fechaPago;
}

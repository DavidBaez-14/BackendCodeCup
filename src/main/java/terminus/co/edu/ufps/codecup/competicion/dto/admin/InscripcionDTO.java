package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscripcionDTO {
    private UUID id;
    private UUID torneoId;
    private String torneoNombre;
    private UUID equipoId;
    private String equipoNombre;
    private String delegadoCedula;
    private String estadoInscripcion;
    private String aprobadoPor;
    private String motivoRechazo;
    private LocalDateTime fechaInscripcion;
    private String expulsadoPor;
    private LocalDateTime fechaExpulsion;
    private String motivoExpulsion;
    private BigDecimal montoInscripcion;
}

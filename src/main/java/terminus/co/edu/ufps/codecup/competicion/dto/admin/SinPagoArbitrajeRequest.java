package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.Data;

import java.util.UUID;

@Data
public class SinPagoArbitrajeRequest {
    private String motivo;
    private UUID equipoNoPagoTorneoId;
}

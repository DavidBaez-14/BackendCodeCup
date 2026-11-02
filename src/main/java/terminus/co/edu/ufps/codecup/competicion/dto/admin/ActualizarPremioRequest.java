package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.Data;

@Data
public class ActualizarPremioRequest {
    private String titulo;
    private String descripcion;
    private Integer monto;
}

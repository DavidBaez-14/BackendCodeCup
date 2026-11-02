package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.Data;

@Data
public class CrearPremioRequest {
    private String codigoCatalogo;
    private String titulo;
    private String descripcion;
    private Integer monto;
}

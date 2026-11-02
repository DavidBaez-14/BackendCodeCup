package terminus.co.edu.ufps.codecup.competicion.dto.delegado;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgregarMiembroRequest {
    private String cedula;
    private Integer numeroCamiseta;
}

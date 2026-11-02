package terminus.co.edu.ufps.codecup.competicion.dto.delegado;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiembroEquipoDTO {
    private String cedula;
    private String nombre;
    private String posicion;
    private String piernaHabil;
    private Integer alturaCm;
    private Integer numeroCamiseta;
    private String estado;
    private LocalDate desde;
    private Boolean esDelegado;
    private String torneoEstado;
    /** false = el delegado lo agregó sin cuenta: aún no puede iniciar sesión (spec 0018). */
    private Boolean tieneCuenta;
}

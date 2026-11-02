package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearTorneoRequest {
    private String nombre;
    private Integer edicion;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}

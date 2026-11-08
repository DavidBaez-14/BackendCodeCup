package terminus.co.edu.ufps.codecup.competicion.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoDTO {
    private UUID id;
    private String nombre;
    private Integer edicion;
    private String estado;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDateTime publicadoEn;
    private String formato;
    /** Spec 0023: el torneo exige documento a sus miembros. */
    private Boolean exigeDocumento;
}

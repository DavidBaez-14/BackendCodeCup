package terminus.co.edu.ufps.codecup.competicion.dto.creador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** "Tu torneo está listo" (spec 0029, MOV-0007): con el enlace para compartir. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoCreadoDTO {
    private UUID id;
    private String nombre;
    private String deporte;
    private String formato;
    /** Nace en PUBLICADO: inscripciones abiertas. */
    private String estado;
    private String visibilidad;
    private Integer cupos;
    private LocalDate fechaInicio;
    private LocalDate cierreInscripciones;
    /** Null si no cobra inscripción. */
    private BigDecimal valorInscripcion;
    private String enlaceToken;
    /** Existe siempre: un torneo público también se comparte con él. */
    private String enlace;
}

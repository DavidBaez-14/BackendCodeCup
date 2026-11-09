package terminus.co.edu.ufps.codecup.competicion.dto.creador;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un torneo en "Mis torneos" de la sección Creador (spec 0024, MOV-0008). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreadorTorneoDTO {
    private UUID id;
    private String nombre;
    private Integer edicion;
    /** BORRADOR, PUBLICADO (inscripciones abiertas), EN_CURSO o FINALIZADO. */
    private String estado;
    private String formato;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    /** Spec 0029: código del deporte. */
    private String deporte;
    /** Spec 0029: PUBLICO, NO_LISTADO o PRIVADO. */
    private String visibilidad;
    /** Spec 0029: máximo de inscripciones aprobadas; null = sin límite. */
    private Integer cupos;
    /** Spec 0029: el enlace para compartir. */
    private String enlace;
    /** Inscripciones aprobadas. */
    private int inscritos;
    private Pendientes pendientes;
    private LocalDateTime creadoEn;

    /**
     * Lo que espera una decisión del organizador.
     *
     * @param inscripciones inscripciones PENDIENTE_PAGO (aprobar o rechazar)
     * @param comprobantes  comprobantes por revisar, de inscripción o de multa
     */
    public record Pendientes(int inscripciones, int comprobantes) {
    }
}

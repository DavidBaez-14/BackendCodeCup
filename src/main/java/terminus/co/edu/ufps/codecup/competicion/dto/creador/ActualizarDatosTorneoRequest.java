package terminus.co.edu.ufps.codecup.competicion.dto.creador;

import java.time.LocalDate;
import lombok.Data;

/**
 * Cambiar los datos básicos de un torneo (spec 0029, Configuración). Lo que llega en null no
 * cambia. Para quitar el límite de cupos o la fecha de cierre, usa {@code sinLimiteDeCupos} o
 * {@code sinCierre}.
 */
@Data
public class ActualizarDatosTorneoRequest {
    private String nombre;
    /** Solo mientras no haya inscripciones. */
    private String deporte;
    private String visibilidad;
    private Integer cupos;
    private Boolean sinLimiteDeCupos;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDate cierreInscripciones;
    private Boolean sinCierre;
    /** Texto libre; {@code ""} lo borra. */
    private String reglas;
}

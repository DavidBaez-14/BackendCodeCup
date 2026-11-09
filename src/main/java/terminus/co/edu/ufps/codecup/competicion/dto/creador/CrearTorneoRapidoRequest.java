package terminus.co.edu.ufps.codecup.competicion.dto.creador;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * Crear un torneo en tres pasos desde el móvil (spec 0029, MOV-0007). Las validaciones las hace el
 * servicio, con un {@code code} por campo, para que la app vuelva al paso correcto.
 */
@Data
public class CrearTorneoRapidoRequest {

    /** Entre 3 y 150 caracteres. */
    private String nombre;

    /** Código del catálogo (GET /api/public/deportes). */
    private String deporte;

    /** LIGA, ELIMINACION_DIRECTA o GRUPOS_ELIMINATORIAS. */
    private String formato;

    /** Máximo de inscripciones aprobadas: 2-64 (liga), 2-16 (eliminación directa), 4-64 (grupos). */
    private Integer cupos;

    /** Hoy o después (hora de Colombia). */
    private LocalDate fechaInicio;

    /** PUBLICO, NO_LISTADO o PRIVADO. Obligatoria: la elige el organizador en el paso 3. */
    private String visibilidad;

    private Boolean cobraInscripcion;

    /** Obligatorio y mayor que cero si {@code cobraInscripcion}. */
    private BigDecimal valorInscripcion;

    /** Opcional: último día para inscribirse; entre hoy y la fecha de inicio. */
    private LocalDate cierreInscripciones;

    /** Opcional (spec 0023): por defecto no exige documento. */
    private Boolean exigeDocumento;

    /** Opcional: hasta 2000 caracteres. */
    private String reglas;
}

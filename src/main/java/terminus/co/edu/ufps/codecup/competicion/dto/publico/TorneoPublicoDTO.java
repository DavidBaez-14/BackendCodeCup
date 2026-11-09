package terminus.co.edu.ufps.codecup.competicion.dto.publico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.PosicionGrupoDTO;

/**
 * Detalle de un torneo (spec 0029, MOV-0005). La parte pública es igual para todos; la franja de
 * acciones la pinta {@link #miRelacion}, que solo llega con sesión. Sin documentos ni correos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoPublicoDTO {
    private UUID id;
    private String nombre;
    private Integer edicion;
    private DeporteDTO deporte;
    private String formato;
    private String estado;
    private String visibilidad;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDate cierreInscripciones;
    private Integer cupos;
    /** Inscripciones aprobadas. */
    private int inscritos;
    private boolean inscripcionesAbiertas;
    private boolean cobraInscripcion;
    private BigDecimal valorInscripcion;
    private boolean exigeDocumento;
    private String reglas;
    /** Nombre visible del creador. */
    private String organizador;
    /** Para compartir. Null en un torneo privado, salvo para sus admins. */
    private String enlace;
    /** Equipos aprobados. */
    private List<EquipoPublico> equipos;
    /** Tabla por grupo ("" para una liga sin grupos). */
    private Map<String, List<PosicionGrupoDTO>> tabla;
    /** En orden de fecha, sin los de descanso. */
    private List<PartidoPublico> partidos;
    private List<PremioPublico> premios;
    /** Null sin sesión. */
    private MiRelacion miRelacion;

    public record EquipoPublico(UUID equipoTorneoId, String nombre, int miembros) {
    }

    public record EquipoEnPartido(UUID equipoTorneoId, String nombre) {
    }

    public record Marcador(int local, int visitante) {
    }

    /** {@code local} y {@code visitante} en null si la llave aún no está definida. */
    public record PartidoPublico(UUID partidoId, String fase, String faseCodigo, Integer jornada, String grupo,
                                 EquipoEnPartido local, EquipoEnPartido visitante, LocalDateTime fechaHora,
                                 String lugar, String estado, Marcador marcador) {
    }

    /** El ganador sale con su nombre visible (spec 0022). */
    public record PremioPublico(UUID id, String codigo, String nombre, String titulo, String descripcion, Integer monto,
                                String ganadorNombre, String ganadorEquipo) {
    }

    /**
     * Relación de quien consulta con el torneo. Un solo campo pinta toda la franja.
     *
     * @param administra es admin (abre la sección Creador)
     * @param arbitra    es árbitro del torneo (abre la sección Árbitro)
     * @param delegadoDe su inscripción como delegado, o null
     * @param juegaEn    el plantel donde está activo, o null
     */
    public record MiRelacion(boolean administra, boolean arbitra, DelegadoDe delegadoDe, JuegaEn juegaEn) {
    }

    /** {@code comprobante}: PENDIENTE_REVISION, APROBADO o RECHAZADO; null si no ha subido. */
    public record DelegadoDe(UUID equipoTorneoId, String equipo, String estadoInscripcion, String comprobante) {
    }

    public record JuegaEn(UUID equipoTorneoId, String equipo, String estadoInscripcion) {
    }
}

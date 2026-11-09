package terminus.co.edu.ufps.codecup.competicion.dto.jugador;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un equipo de la persona en un torneo, como jugador o como delegado (spec 0026, "Mis equipos"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiEquipoDTO {
    private UUID equipoTorneoId;
    private UUID equipoId;
    private String equipo;
    private UUID torneoId;
    private String torneo;
    /** Spec 0029: código del deporte del torneo. */
    private String deporte;
    /** BORRADOR, PUBLICADO, EN_CURSO o FINALIZADO. */
    private String estadoTorneo;
    /** DELEGADO (gestiona la inscripción; también juega) o JUGADOR. */
    private String rol;
    /** PENDIENTE_PAGO, APROBADO, RECHAZADO o EXPULSADO. */
    private String estadoInscripcion;
    private String motivoRechazo;
    /** Solo para el delegado: PENDIENTE_REVISION, APROBADO o RECHAZADO; null si no ha subido. */
    private String comprobante;
    private Integer numeroCamiseta;
    /** El próximo partido por jugar del equipo, o null. */
    private ProximoPartido proximoPartido;

    /**
     * @param rival null si el cruce aún no está definido
     * @param local el equipo juega de local
     */
    public record ProximoPartido(UUID partidoId, String rival, boolean local, LocalDateTime fechaHora,
                                 String lugar, String estado) {
    }
}

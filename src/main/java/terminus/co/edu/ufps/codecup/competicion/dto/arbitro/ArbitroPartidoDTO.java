package terminus.co.edu.ufps.codecup.competicion.dto.arbitro;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un partido en la sección Árbitro (spec 0025, MOV-0009). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbitroPartidoDTO {
    private UUID partidoId;
    private UUID torneoId;
    private String torneo;
    /** Spec 0029: código del deporte del torneo. */
    private String deporte;
    /** Para mostrar: "Grupo A · Fecha 3", "Fecha 2", "Semifinal"… */
    private String fase;
    /** GRUPOS, OCTAVOS, CUARTOS, SEMIS, FINAL, TERCER_PUESTO o REPECHAJE. */
    private String faseCodigo;
    private Integer jornada;
    private String grupo;
    /** Null mientras el cruce no está definido (llave por jugarse). */
    private EquipoEnPartido local;
    private EquipoEnPartido visitante;
    private LocalDateTime fechaHora;
    private String lugar;
    /** PROGRAMADO, EN_CURSO, FINALIZADO, APLAZADO o WO. */
    private String estado;
    /** Null mientras está PROGRAMADO. */
    private Marcador marcador;
    /** Se lo asignaron a esta cuenta (false si lo ve por el permiso "todos los partidos"). */
    private boolean asignado;

    public record EquipoEnPartido(UUID equipoTorneoId, String nombre) {
    }

    public record Marcador(int local, int visitante) {
    }
}

package terminus.co.edu.ufps.codecup.competicion.dto.arbitro;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.EventoDTO;

/**
 * Todo lo que necesita la planilla del árbitro en una sola respuesta (spec 0025, MOV-0009): el
 * partido, los dos planteles con quién puede jugar, la alineación, los eventos en orden y los tipos
 * de evento del deporte. Las acciones siguen en {@code /api/supercopa/admin/partidos/{id}/...}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanillaDTO {

    private ArbitroPartidoDTO partido;

    /** Tipos de evento que se pueden registrar, en el orden en que se muestran. */
    private List<String> tiposEvento;

    private Equipo local;
    private Equipo visitante;

    /** En orden de registro (sin minuto). */
    private List<EventoDTO> eventos;

    /** Se pueden registrar eventos y cambiar la alineación (no está cerrado ni en descanso). */
    private boolean editable;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Equipo {
        private UUID equipoTorneoId;
        private String nombre;
        private List<Jugador> jugadores;
    }

    /**
     * Un miembro activo del plantel. {@code apto} y {@code motivos} salen de la misma regla que usa
     * el backend al alinear (suspensión por roja o multa pendiente): si {@code apto} es false, la
     * app lo marca y no deja alinearlo.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Jugador {
        private UUID personaId;
        private String nombre;
        private Integer numeroCamiseta;
        /** Para verificar la identidad en la cancha. Null si el delegado no lo registró. */
        private String numeroDocumento;
        private boolean esDelegado;
        private boolean alineado;
        private int goles;
        private boolean apto;
        private List<String> motivos;
    }
}

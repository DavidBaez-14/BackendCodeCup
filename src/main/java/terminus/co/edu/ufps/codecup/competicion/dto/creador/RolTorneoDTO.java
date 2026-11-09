package terminus.co.edu.ufps.codecup.competicion.dto.creador;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un admin (o árbitro) de un torneo, como lo ve otro admin del mismo torneo (spec 0024, N-24). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolTorneoDTO {
    /** Id del rol: se usa para quitarlo. */
    private UUID id;
    private UUID cuentaId;
    private UUID personaId;
    private String nombre;
    private String correo;
    /** ADMIN o ARBITRO. */
    private String rol;
    /** Solo árbitros (spec 0025): puede operar todos los partidos del torneo. */
    private Boolean todosLosPartidos;
    private LocalDateTime creadoEn;
    /** Es quien llama: la app marca "Tú". */
    private boolean esTuyo;
}

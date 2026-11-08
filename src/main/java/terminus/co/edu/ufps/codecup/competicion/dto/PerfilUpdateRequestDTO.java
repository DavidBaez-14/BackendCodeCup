package terminus.co.edu.ufps.codecup.competicion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
/**
 * Perfil deportivo del jugador. Los tres campos son opcionales (spec 0020):
 * el PUT los reemplaza, asi que un campo en null queda sin indicar.
 */
public class PerfilUpdateRequestDTO {

    @Min(100)
    @Max(250)
    private Integer alturaCm;

    @Pattern(regexp = "DERECHA|IZQUIERDA|AMBIDIESTRO",
            message = "piernaHabil debe ser DERECHA, IZQUIERDA o AMBIDIESTRO")
    private String piernaHabil;

    @Pattern(regexp = "PORTERO|DEFENSA|MEDIOCAMPISTA|DELANTERO",
            message = "posicion debe ser PORTERO, DEFENSA, MEDIOCAMPISTA o DELANTERO")
    private String posicion;
}

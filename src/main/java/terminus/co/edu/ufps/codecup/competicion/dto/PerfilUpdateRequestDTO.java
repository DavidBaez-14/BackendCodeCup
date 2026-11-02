package terminus.co.edu.ufps.codecup.competicion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PerfilUpdateRequestDTO {

    @NotNull
    @Min(100)
    @Max(250)
    private Integer alturaCm;

    @NotNull
    @Pattern(regexp = "DERECHA|IZQUIERDA|AMBIDIESTRO",
            message = "piernaHabil debe ser DERECHA, IZQUIERDA o AMBIDIESTRO")
    private String piernaHabil;

    @NotNull
    @Pattern(regexp = "PORTERO|DEFENSA|MEDIOCAMPISTA|DELANTERO",
            message = "posicion debe ser PORTERO, DEFENSA, MEDIOCAMPISTA o DELANTERO")
    private String posicion;
}

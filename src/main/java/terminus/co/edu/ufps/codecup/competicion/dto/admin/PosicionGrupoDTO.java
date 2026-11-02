package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosicionGrupoDTO {
    private Integer posicion;
    private UUID equipoTorneoId;
    private String equipoNombre;
    private String grupo;

    private Integer pts;
    private Integer pj;
    private Integer pg;
    private Integer pe;
    private Integer pp;
    private Integer gf;
    private Integer gc;
    private Integer dg;
    private Integer rojas;

    /** Últimos 3 resultados en orden cronológico inverso (más reciente primero). 'W' | 'D' | 'L'. */
    private List<String> form;

    private Boolean descalificado;
}

package terminus.co.edu.ufps.codecup.analytics.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosicionDTO {
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
    private List<String> form;
    private Boolean descalificado;
}

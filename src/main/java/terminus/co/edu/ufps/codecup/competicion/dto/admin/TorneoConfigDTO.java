package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoConfigDTO {
    private UUID torneoId;
    private String torneoNombre;
    private String estado;
    private String formato;
    private Integer numGrupos;
    private Integer clasificanPorGrupo;
    private Boolean repechaje;
    private List<String> rondasPlayoff;

    private Integer equiposAprobados;
    private boolean fixtureGenerado;
    private boolean hayPartidoJugado;

    private boolean formatoEditable;
    private boolean fixtureRegenerable;
    private String motivoBloqueoFormato;
    private String motivoBloqueoFixture;
}

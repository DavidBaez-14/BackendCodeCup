package terminus.co.edu.ufps.codecup.competicion.dto.admin;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuardarConfigRequest {
    private String formato;
    private Integer numGrupos;
    private Integer clasificanPorGrupo;
    private Boolean repechaje;
    private List<String> rondasPlayoff;
}

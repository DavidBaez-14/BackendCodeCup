package terminus.co.edu.ufps.codecup.finanzas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElegibilidadResponse {
    private boolean apto;
    private List<String> motivos;
}

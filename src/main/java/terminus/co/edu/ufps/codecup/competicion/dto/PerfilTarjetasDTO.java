package terminus.co.edu.ufps.codecup.competicion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilTarjetasDTO {
    private int amarillas;
    private int azules;
    private int rojas;
}

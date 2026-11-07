package terminus.co.edu.ufps.codecup.competicion.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilResponseDTO {
    private String cedula;
    private String nombre;
    // Datos deportivos del Jugador local. Opcionales (spec 0020): null si el
    // jugador aun no los indico.
    private Integer alturaCm;
    private String piernaHabil;
    private String posicion;
    private List<PerfilEquipoDTO> equipos;
    private PerfilResumenDTO resumen;
    private List<PerfilTituloDTO> titulos;
    private List<PerfilPartidoDTO> partidos;
}

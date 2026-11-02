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
    // Datos deportivos del Jugador local. Null si aun no completo su perfil
    // (el frontend usa esto para mostrar el modal bloqueante de bienvenida).
    private Integer alturaCm;
    private String piernaHabil;
    private String posicion;
    private List<PerfilEquipoDTO> equipos;
    private PerfilResumenDTO resumen;
    private List<PerfilTituloDTO> titulos;
    private List<PerfilPartidoDTO> partidos;
}

package terminus.co.edu.ufps.codecup.competicion.dto.publico;

/** Un deporte del catálogo (spec 0029). {@code tipoCompetencia}: EQUIPO o INDIVIDUAL. */
public record DeporteDTO(String codigo, String nombre, String tipoCompetencia) {
}

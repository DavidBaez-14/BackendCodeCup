package terminus.co.edu.ufps.codecup.competicion.dto.publico;

import java.util.List;

/** Una página de resultados (spec 0029). {@code pagina} empieza en 0. */
public record PaginaDTO<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
}

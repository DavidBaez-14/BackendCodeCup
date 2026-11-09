package terminus.co.edu.ufps.codecup.competicion.dto.publico;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un torneo en Explorar (spec 0029, MOV-0005). Sin datos personales: el organizador sale con su
 * nombre visible (spec 0022).
 *
 * @param inscritos             inscripciones aprobadas
 * @param inscripcionesAbiertas publicado, sin pasar el cierre y con cupo
 */
public record TorneoResumenPublicoDTO(UUID id, String nombre, String deporte, String deporteNombre, String formato,
                                      String estado, Integer cupos, int inscritos, LocalDate cierreInscripciones,
                                      LocalDate inicio, String organizador, boolean inscripcionesAbiertas) {
}

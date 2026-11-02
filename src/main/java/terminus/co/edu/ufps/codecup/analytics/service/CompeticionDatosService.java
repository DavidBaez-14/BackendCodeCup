package terminus.co.edu.ufps.codecup.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.competicion.dto.admin.AsignarPremioRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.PosicionGrupoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.PremioDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.service.ClasificacionService;
import terminus.co.edu.ufps.codecup.competicion.service.PremioService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acceso de MS4 a los datos de competición de MS2.
 *
 * <p>Sustituye al antiguo {@code Ms2InternalClient}, que hablaba por HTTP contra
 * {@code /api/supercopa/internal/**} con un shared secret. Al vivir MS2 y MS4 en el
 * mismo binario, esas llamadas eran un salto de red del proceso contra sí mismo.
 *
 * <p><b>Contexto histórico relevante:</b> de los seis métodos del cliente viejo, solo
 * dos apuntaban a endpoints que MS2 realmente exponía ({@code /premios} y
 * {@code /premios/{id}/asignar}). Los otros cuatro daban 404, la excepción se tragaba
 * en un {@code catch} y se devolvía vacío — por eso las tablas {@code analytics.snapshot_*}
 * nunca se llenaron. Aquí se implementan de verdad los que tienen una fuente de datos
 * inequívoca; goleadores y porteros quedan explícitamente pendientes (spec 0016).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompeticionDatosService {

    private final ClasificacionService clasificacionService;
    private final PremioService premioService;
    private final PartidoRepository partidoRepo;
    private final EventoPartidoRepository eventoRepo;

    /** Clasificación por grupo. Para formato LIGA la clave es {@code "GLOBAL"}. */
    @Transactional(readOnly = true)
    public Map<String, List<PosicionGrupoDTO>> obtenerClasificacion(UUID torneoId) {
        return clasificacionService.calcularPorGrupo(torneoId);
    }

    /** Premios configurados del torneo, con su ganador si ya fue asignado. */
    @Transactional(readOnly = true)
    public List<PremioDTO> obtenerPremios(UUID torneoId) {
        return premioService.listarPremiosPublicos(torneoId);
    }

    /** Asigna el ganador de un premio del torneo. */
    @Transactional
    public void asignarPremio(UUID torneoId, UUID torneoPremioId, AsignarPremioRequest req) {
        premioService.asignarPremio(torneoId, torneoPremioId, req);
    }

    /**
     * Partidos del torneo con su marcador resuelto.
     *
     * <p>El marcador no es una columna de {@code supercopa.partidos}: se deriva contando
     * eventos {@code GOL}, igual que hace {@link ClasificacionService}. Los goles ficticios
     * del W.O. ya están registrados como eventos, así que no necesitan caso especial.
     */
    @Transactional(readOnly = true)
    public List<ResultadoPartido> obtenerPartidos(UUID torneoId) {
        List<ResultadoPartido> resultados = new ArrayList<>();
        for (Partido p : partidoRepo.findByTorneoId(torneoId)) {
            EquipoTorneo local = p.getEquipoLocalTorneo();
            EquipoTorneo visitante = p.getEquipoVisitanteTorneo();
            resultados.add(new ResultadoPartido(
                    p.getId(),
                    p.getFase() == null ? null : p.getFase().name(),
                    p.getEstado() == null ? null : p.getEstado().name(),
                    idDe(local),
                    idDe(visitante),
                    nombreDe(local),
                    nombreDe(visitante),
                    contarGoles(p.getId(), local),
                    contarGoles(p.getId(), visitante)));
        }
        return resultados;
    }

    /**
     * Goleadores agregados del torneo.
     *
     * <p><b>Pendiente (spec 0016).</b> Devuelve vacío, que es exactamente lo que devolvía
     * el cliente HTTP (el endpoint de MS2 nunca existió). Implementarlo requiere decidir
     * antes la semántica: los goles de W.O. se registran con {@code cedula = null}, y hay
     * que fijar los criterios de desempate entre jugadores con los mismos goles.
     */
    public List<GoleadorAgregado> obtenerGoleadores(UUID torneoId) {
        log.debug("obtenerGoleadores({}): sin implementar, pendiente spec 0016", torneoId);
        return List.of();
    }

    /**
     * Agregados de valla menos vencida por equipo.
     *
     * <p><b>Pendiente (spec 0016).</b> Mismo caso que {@link #obtenerGoleadores(UUID)}:
     * falta decidir si los partidos en estado {@code DESCANSO} cuentan como partido jugado
     * y cómo se tratan los goles administrativos del W.O.
     */
    public List<PorteroAgregado> obtenerPorteros(UUID torneoId) {
        log.debug("obtenerPorteros({}): sin implementar, pendiente spec 0016", torneoId);
        return List.of();
    }

    private int contarGoles(UUID partidoId, EquipoTorneo equipoTorneo) {
        if (equipoTorneo == null) return 0;
        return (int) eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                partidoId, equipoTorneo.getId(), TipoEvento.GOL);
    }

    private static UUID idDe(EquipoTorneo et) {
        return et == null ? null : et.getId();
    }

    private static String nombreDe(EquipoTorneo et) {
        return et == null || et.getEquipo() == null ? null : et.getEquipo().getNombre();
    }

    /** Partido del torneo con marcador ya resuelto. */
    public record ResultadoPartido(
            UUID partidoId,
            String fase,
            String estado,
            UUID equipoLocalTorneoId,
            UUID equipoVisitanteTorneoId,
            String equipoLocalNombre,
            String equipoVisitanteNombre,
            int golesLocal,
            int golesVisitante) {}

    /** Goles acumulados de un jugador en el torneo. */
    public record GoleadorAgregado(
            String cedula,
            String jugadorNombre,
            String equipoNombre,
            int goles) {}

    /** Goles en contra acumulados de un equipo en el torneo. */
    public record PorteroAgregado(
            UUID equipoTorneoId,
            String equipoNombre,
            int golesEnContra,
            int partidosJugados) {}
}

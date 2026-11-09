package terminus.co.edu.ufps.codecup.competicion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroAsignadoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroPartidoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.PlanillaDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ProgramarPartidoRequest;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoArbitro;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.finanzas.service.MultaService;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Árbitros por partido (spec 0025): qué partidos ve cada árbitro, la planilla en una sola
 * respuesta, y la asignación y programación que hace el admin del torneo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrajeService {

    /** Estados en los que un partido ya no admite cambios. */
    private static final Set<EstadoPartido> CERRADOS = Set.of(EstadoPartido.FINALIZADO, EstadoPartido.WO);

    private final PartidoRepository partidoRepo;
    private final PartidoArbitroRepository partidoArbitroRepo;
    private final TorneoRolRepository rolRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final PartidoJugadorRepository partidoJugadorRepo;
    private final EventoPartidoRepository eventoRepo;
    private final DirectorioPersonas directorioPersonas;
    private final MultaService multaService;
    private final PartidoAdminService partidoAdminService;

    // ── Sección Árbitro ─────────────────────────────────────────────────

    /**
     * Los partidos que la cuenta arbitra: los asignados y los de los torneos donde tiene
     * "todos los partidos". Sin los que quedaron en descanso. En orden de fecha.
     *
     * @param desde opcional, desde ese día (incluido)
     * @param hasta opcional, hasta ese día (incluido)
     */
    @Transactional(readOnly = true)
    public List<ArbitroPartidoDTO> misPartidos(UUID cuentaId, LocalDate desde, LocalDate hasta) {
        Set<UUID> asignados = partidoArbitroRepo.findByCuentaId(cuentaId).stream()
                .map(pa -> pa.getPartido().getId())
                .collect(Collectors.toSet());
        Map<UUID, Partido> partidos = new LinkedHashMap<>();
        partidoRepo.findAllById(asignados).forEach(p -> partidos.put(p.getId(), p));
        for (var rol : rolRepo.findByCuentaIdAndRol(cuentaId, RolTorneo.ARBITRO)) {
            if (Boolean.TRUE.equals(rol.getTodosLosPartidos())) {
                partidoRepo.findByTorneoIdOrderByFechaAsc(rol.getTorneo().getId())
                        .forEach(p -> partidos.putIfAbsent(p.getId(), p));
            }
        }
        return partidos.values().stream()
                .filter(p -> p.getEstado() != EstadoPartido.DESCANSO)
                .filter(p -> desde == null || !p.getFecha().toLocalDate().isBefore(desde))
                .filter(p -> hasta == null || !p.getFecha().toLocalDate().isAfter(hasta))
                .sorted(Comparator.comparing(Partido::getFecha))
                .map(p -> toDTO(p, asignados.contains(p.getId())))
                .toList();
    }

    /** La planilla completa de un partido. Quien llama ya pasó por {@code authz.arbitroDePartido}. */
    @Transactional(readOnly = true)
    public PlanillaDTO planilla(UUID partidoId, UUID cuentaId) {
        var p = partido(partidoId);
        UUID torneoId = p.getTorneo().getId();
        Map<UUID, PartidoJugador> alineacion = partidoJugadorRepo.findByPartidoId(partidoId).stream()
                .collect(Collectors.toMap(PartidoJugador::getPersonaId, Function.identity(), (a, b) -> a));
        return PlanillaDTO.builder()
                .partido(toDTO(p, partidoArbitroRepo.existsByPartidoIdAndCuentaId(partidoId, cuentaId)))
                .tiposEvento(Arrays.stream(TipoEvento.values()).map(Enum::name).toList())
                .local(equipo(p.getEquipoLocalTorneo(), torneoId, alineacion))
                .visitante(equipo(p.getEquipoVisitanteTorneo(), torneoId, alineacion))
                .eventos(partidoAdminService.listarEventos(partidoId))
                .editable(!CERRADOS.contains(p.getEstado()) && p.getEstado() != EstadoPartido.DESCANSO)
                .build();
    }

    // ── Asignación (admin del torneo) ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<ArbitroAsignadoDTO> arbitrosDe(UUID partidoId) {
        partido(partidoId);
        var asignados = partidoArbitroRepo.findByPartidoIdOrderByAsignadoEnAsc(partidoId);
        var nombres = directorioPersonas.nombresDeCuentas(asignados.stream().map(PartidoArbitro::getCuentaId).toList());
        return asignados.stream()
                .map(pa -> new ArbitroAsignadoDTO(pa.getCuentaId(), nombres.get(pa.getCuentaId()), pa.getAsignadoEn()))
                .toList();
    }

    /**
     * Asigna un árbitro del torneo a un partido. Idempotente. 409 NO_ES_ARBITRO_DEL_TORNEO si no lo
     * invitaron al torneo; 409 ARBITRO_EN_PLANTILLA si juega en uno de los dos equipos; 409
     * PARTIDO_CERRADO si ya terminó.
     */
    @Transactional
    public List<ArbitroAsignadoDTO> asignar(UUID partidoId, UUID cuentaArbitro, UUID quienAsigna) {
        var p = partido(partidoId);
        exigirAbierto(p);
        UUID torneoId = p.getTorneo().getId();
        if (!rolRepo.existsByTorneoIdAndCuentaIdAndRol(torneoId, cuentaArbitro, RolTorneo.ARBITRO)) {
            throw ApiException.conflict("NO_ES_ARBITRO_DEL_TORNEO",
                    "Esa persona no es árbitro de este torneo. Invítala primero como árbitro.");
        }
        var cuenta = directorioPersonas.cuentas(List.of(cuentaArbitro)).get(cuentaArbitro);
        if (cuenta != null && cuenta.personaId() != null
                && (enPlantel(cuenta.personaId(), p.getEquipoLocalTorneo())
                    || enPlantel(cuenta.personaId(), p.getEquipoVisitanteTorneo()))) {
            throw ApiException.conflict("ARBITRO_EN_PLANTILLA",
                    "Esa persona juega en uno de los equipos del partido: no puede arbitrarlo.");
        }
        if (!partidoArbitroRepo.existsByPartidoIdAndCuentaId(partidoId, cuentaArbitro)) {
            partidoArbitroRepo.save(PartidoArbitro.builder()
                    .partido(p)
                    .cuentaId(cuentaArbitro)
                    .asignadoEn(LocalDateTime.now())
                    .asignadoPorCuentaId(quienAsigna)
                    .build());
            log.info("[AUDIT][ARBITRO_ASIGNADO] partido={} arbitro={} por={}", partidoId, cuentaArbitro, quienAsigna);
        }
        return arbitrosDe(partidoId);
    }

    @Transactional
    public List<ArbitroAsignadoDTO> quitar(UUID partidoId, UUID cuentaArbitro, UUID quien) {
        partido(partidoId);
        var asignacion = partidoArbitroRepo.findByPartidoIdAndCuentaId(partidoId, cuentaArbitro)
                .orElseThrow(() -> new ResourceNotFoundException("Ese árbitro no está asignado a este partido."));
        partidoArbitroRepo.delete(asignacion);
        log.info("[AUDIT][ARBITRO_QUITADO] partido={} arbitro={} por={}", partidoId, cuentaArbitro, quien);
        return arbitrosDe(partidoId);
    }

    /** Cuándo y dónde se juega. 409 PARTIDO_CERRADO si ya terminó. */
    @Transactional
    public ArbitroPartidoDTO programar(UUID partidoId, ProgramarPartidoRequest req) {
        var p = partido(partidoId);
        exigirAbierto(p);
        if (req.getFecha() != null) {
            p.setFecha(req.getFecha());
        }
        if (req.getLugar() != null) {
            String lugar = req.getLugar().trim();
            p.setLugar(lugar.isEmpty() ? null : lugar);
        }
        partidoRepo.save(p);
        return toDTO(p, false);
    }

    // ── Mapeos ──────────────────────────────────────────────────────────

    public ArbitroPartidoDTO toDTO(Partido p, boolean asignado) {
        var local = p.getEquipoLocalTorneo();
        var visitante = p.getEquipoVisitanteTorneo();
        ArbitroPartidoDTO.Marcador marcador = null;
        if (p.getEstado() != EstadoPartido.PROGRAMADO) {
            marcador = new ArbitroPartidoDTO.Marcador(goles(p, local), goles(p, visitante));
        }
        return ArbitroPartidoDTO.builder()
                .partidoId(p.getId())
                .torneoId(p.getTorneo().getId())
                .torneo(p.getTorneo().getNombre())
                .fase(EtiquetasPartido.fase(p))
                .faseCodigo(p.getFase() != null ? p.getFase().name() : null)
                .jornada(p.getJornada())
                .grupo(p.getGrupo())
                .local(local == null ? null : new ArbitroPartidoDTO.EquipoEnPartido(local.getId(), local.getEquipo().getNombre()))
                .visitante(visitante == null ? null
                        : new ArbitroPartidoDTO.EquipoEnPartido(visitante.getId(), visitante.getEquipo().getNombre()))
                .fechaHora(p.getFecha())
                .lugar(p.getLugar())
                .estado(p.getEstado().name())
                .marcador(marcador)
                .asignado(asignado)
                .build();
    }

    private int goles(Partido p, EquipoTorneo et) {
        if (et == null) return 0;
        return (int) eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(p.getId(), et.getId(), TipoEvento.GOL);
    }

    private PlanillaDTO.Equipo equipo(EquipoTorneo et, UUID torneoId, Map<UUID, PartidoJugador> alineacion) {
        if (et == null) return null;
        List<JugadorEquipo> plantel = new ArrayList<>(jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(et.getId()));
        Set<UUID> activos = new HashSet<>();
        plantel.removeIf(je -> {
            // Quedan los activos y quien ya estaba alineado aunque después lo retiraran.
            boolean queda = je.getEstado() == EstadoMembresia.ACTIVO || alineacion.containsKey(je.getPersonaId());
            if (queda) activos.add(je.getPersonaId());
            return !queda;
        });
        Map<UUID, PersonaResumen> personas = directorioPersonas.buscarVarias(activos);
        var jugadores = plantel.stream()
                .sorted(Comparator.comparing(JugadorEquipo::getNumeroCamiseta, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(je -> {
                    var persona = personas.get(je.getPersonaId());
                    var pj = alineacion.get(je.getPersonaId());
                    var elegibilidad = multaService.consultarElegibilidad(je.getPersonaId(), torneoId);
                    return PlanillaDTO.Jugador.builder()
                            .personaId(je.getPersonaId())
                            .nombre(nombre(persona, je))
                            .numeroCamiseta(je.getNumeroCamiseta())
                            .numeroDocumento(persona != null ? persona.numeroDocumento() : null)
                            .esDelegado(je.getPersonaId().equals(et.getDelegadoPersonaId()))
                            .alineado(pj != null && Boolean.TRUE.equals(pj.getJugo()))
                            .goles(pj != null && pj.getGoles() != null ? pj.getGoles() : 0)
                            .apto(elegibilidad.apto())
                            .motivos(elegibilidad.motivos())
                            .build();
                })
                .toList();
        return PlanillaDTO.Equipo.builder()
                .equipoTorneoId(et.getId())
                .nombre(et.getEquipo().getNombre())
                .jugadores(jugadores)
                .build();
    }

    /** Spec 0018: el nombre registrado gana; si no tiene cuenta, el que escribió el delegado. */
    private static String nombre(PersonaResumen persona, JugadorEquipo je) {
        if (persona != null && persona.tieneCuenta()) return persona.nombreCompleto();
        if (je.getNombreSnapshot() != null && !je.getNombreSnapshot().isBlank()) return je.getNombreSnapshot();
        return persona != null ? persona.nombreCompleto() : null;
    }

    private boolean enPlantel(UUID personaId, EquipoTorneo et) {
        return et != null && jugadorEquipoRepo.findByPersonaIdAndEquipoTorneoId(personaId, et.getId())
                .map(je -> je.getEstado() == EstadoMembresia.ACTIVO)
                .orElse(false);
    }

    private Partido partido(UUID partidoId) {
        return partidoRepo.findById(partidoId).orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
    }

    private static void exigirAbierto(Partido p) {
        if (CERRADOS.contains(p.getEstado())) {
            throw ApiException.conflict("PARTIDO_CERRADO", "El partido ya terminó: no se puede cambiar.");
        }
    }
}

package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.*;
import terminus.co.edu.ufps.codecup.competicion.model.*;
import terminus.co.edu.ufps.codecup.competicion.repository.*;

import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.UUID;
import terminus.co.edu.ufps.codecup.finanzas.service.MultaService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartidoAdminService {

    private final PartidoRepository partidoRepo;
    private final EventoPartidoRepository eventoRepo;
    private final PartidoJugadorRepository partidoJugadorRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final DirectorioPersonas directorioPersonas;
    private final FixtureService fixtureService;
    private final BracketAutoFillService bracketAutoFillService;
    private final terminus.co.edu.ufps.codecup.finanzas.service.MultaService multaService;
    private final terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository partidoArbitroRepo;

    @Transactional(readOnly = true)
    public List<PartidoAdminDTO> listarPorTorneo(UUID torneoId) {
        return partidoRepo.findByTorneoIdOrderByFechaAsc(torneoId)
                .stream()
                .map(this::toPartidoDTO)
                .toList();
    }

    // ── Eventos ─────────────────────────────────────────────────

    @Transactional
    public EventoDTO crearEvento(UUID partidoId, EventoRequest req) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("No se pueden agregar eventos a un partido cerrado.");
        }
        if (partido.getEstado() == EstadoPartido.DESCANSO) {
            throw new RuntimeException("Este partido quedo en DESCANSO porque un equipo fue descalificado.");
        }
        if (req.getPersonaId() == null) {
            throw new RuntimeException("El jugador (personaId) es obligatorio.");
        }
        if (req.getEquipoTorneoId() == null) {
            throw new RuntimeException("El equipo (equipoTorneoId) es obligatorio.");
        }
        TipoEvento tipo;
        try {
            tipo = TipoEvento.valueOf(req.getTipoEvento());
        } catch (Exception e) {
            throw new RuntimeException("Tipo de evento invalido. Valores validos: GOL, AMARILLA, AZUL, ROJA.");
        }

        UUID personaId = req.getPersonaId();
        directorioPersonas.buscar(personaId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado."));
        var equipoTorneo = equipoTorneoRepo.findById(req.getEquipoTorneoId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));

        boolean esLocal = partido.getEquipoLocalTorneo().getId().equals(equipoTorneo.getId());
        boolean esVisitante = partido.getEquipoVisitanteTorneo().getId().equals(equipoTorneo.getId());
        if (!esLocal && !esVisitante) {
            throw new RuntimeException("El equipo no participa en este partido.");
        }

        Integer max = eventoRepo.findMaxOrdenByPartidoId(partidoId);
        int orden = (max == null ? 0 : max) + 1;

        var evento = EventoPartido.builder()
                .partido(partido)
                .personaId(personaId)
                .equipoTorneo(equipoTorneo)
                .tipoEvento(tipo)
                .orden(orden)
                .build();
        eventoRepo.save(evento);

        if (tipo == TipoEvento.GOL) {
            var pj = partidoJugadorRepo.findByPartidoIdAndPersonaId(partidoId, personaId)
                    .orElseGet(() -> PartidoJugador.builder()
                            .partido(partido)
                            .personaId(personaId)
                            .equipoTorneo(equipoTorneo)
                            .jugo(true)
                            .goles(0)
                            .build());
            pj.setGoles((pj.getGoles() == null ? 0 : pj.getGoles()) + 1);
            partidoJugadorRepo.save(pj);
        }

        if (partido.getEstado() == EstadoPartido.PROGRAMADO) {
            partido.setEstado(EstadoPartido.EN_CURSO);
            partidoRepo.save(partido);
        }

        return toEventoDTO(evento);
    }

    @Transactional(readOnly = true)
    public List<EventoDTO> listarEventos(UUID partidoId) {
        partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        return eventoRepo.findByPartidoIdOrderByOrdenAsc(partidoId)
                .stream()
                .map(this::toEventoDTO)
                .toList();
    }

    @Transactional
    public void eliminarEvento(UUID partidoId, UUID eventoId) {
        var evento = eventoRepo.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado."));
        if (!evento.getPartido().getId().equals(partidoId)) {
            throw new RuntimeException("El evento no pertenece al partido indicado.");
        }
        if (evento.getTipoEvento() == TipoEvento.GOL) {
            var pj = partidoJugadorRepo.findByPartidoIdAndPersonaId(partidoId, evento.getPersonaId())
                    .orElse(null);
            if (pj != null && pj.getGoles() != null && pj.getGoles() > 0) {
                pj.setGoles(pj.getGoles() - 1);
                partidoJugadorRepo.save(pj);
            }
        }
        eventoRepo.delete(evento);
    }

    @Transactional
    public PartidoAdminDTO cerrarPartido(UUID partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            return toPartidoDTO(partido);
        }
        if (partido.getEstado() == EstadoPartido.DESCANSO) {
            throw new RuntimeException("Este partido quedo en DESCANSO y no puede cerrarse con resultado.");
        }
        asegurarParticipantes(partido, partido.getEquipoLocalTorneo());
        asegurarParticipantes(partido, partido.getEquipoVisitanteTorneo());
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoRepo.save(partido);

        // Auto-avance en bracket eliminatorio
        if (partido.getSiguientePartido() != null
                && partido.getEquipoLocalTorneo() != null
                && partido.getEquipoVisitanteTorneo() != null) {
            long golesLocal = eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                    partido.getId(), partido.getEquipoLocalTorneo().getId(), TipoEvento.GOL);
            long golesVisitante = eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                    partido.getId(), partido.getEquipoVisitanteTorneo().getId(), TipoEvento.GOL);
            EquipoTorneo ganador = null;
            if (golesLocal > golesVisitante) {
                ganador = partido.getEquipoLocalTorneo();
            } else if (golesVisitante > golesLocal) {
                ganador = partido.getEquipoVisitanteTorneo();
            }
            if (ganador != null) {
                fixtureService.avanzarGanador(partido, ganador);
            }
        }

        // Generar multas automaticas si no es WO ni DESCANSO ni SIN_PAGO_ARBITRAJE
        if (partido.getEstado() != EstadoPartido.WO
                && partido.getEstado() != EstadoPartido.DESCANSO
                && partido.getTipoCierre() == null) {
            try {
                multaService.generarPorPartido(partido.getId());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PartidoAdminService.class)
                        .error("Multa gen fallo para partido {}: {}", partido.getId(), e.getMessage(), e);
            }
        }

        // Decrementar suspensiones de ambos equipos si el partido finalizo normalmente
        if (partido.getEstado() == EstadoPartido.FINALIZADO && partido.getTipoCierre() == null) {
            try {
                if (partido.getEquipoLocalTorneo() != null) {
                    multaService.decrementarSuspensiones(partido.getEquipoLocalTorneo().getId());
                }
                if (partido.getEquipoVisitanteTorneo() != null) {
                    multaService.decrementarSuspensiones(partido.getEquipoVisitanteTorneo().getId());
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PartidoAdminService.class)
                        .error("Decremento de suspension fallo para partido {}: {}", partido.getId(), e.getMessage(), e);
            }
        }

        // Si el partido que se cierra es de fase GRUPOS, intentar poblar el bracket.
        // Try/catch defensivo: si falla, no rompe el cierre del partido.
        if (partido.getFase() == terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo.GRUPOS
                && partido.getTorneo() != null) {
            try {
                bracketAutoFillService.poblarSiFaseGruposCompleta(partido.getTorneo().getId());
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(PartidoAdminService.class)
                        .error("Auto-fill bracket fallo para torneo {}: {}",
                                partido.getTorneo().getId(), ex.getMessage(), ex);
            }
        }

        return toPartidoDTO(partido);
    }

    // ── Alineación ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PartidoJugadorDTO> listarAlineacion(UUID partidoId) {
        partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        return partidoJugadorRepo.findByPartidoId(partidoId)
                .stream()
                .map(this::toPartidoJugadorDTO)
                .toList();
    }

    @Transactional
    public void agregarJugador(UUID partidoId, UUID personaId, UUID equipoTorneoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("No se puede modificar la alineacion de un partido cerrado.");
        }

        var el = multaService.consultarElegibilidad(personaId, partido.getTorneo().getId());
        if (!el.apto()) {
            throw new RuntimeException("Jugador no apto: " + el.formatMotivos());
        }

        var equipoTorneo = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));
        directorioPersonas.buscar(personaId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado."));

        var existente = partidoJugadorRepo.findByPartidoIdAndPersonaId(partidoId, personaId);
        if (existente.isPresent()) {
            var pj = existente.get();
            pj.setJugo(true);
            partidoJugadorRepo.save(pj);
        } else {
            partidoJugadorRepo.save(PartidoJugador.builder()
                    .partido(partido)
                    .personaId(personaId)
                    .equipoTorneo(equipoTorneo)
                    .jugo(true)
                    .goles(0)
                    .build());
        }
    }

    @Transactional
    public void quitarJugador(UUID partidoId, UUID personaId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("No se puede modificar la alineacion de un partido cerrado.");
        }
        var pj = partidoJugadorRepo.findByPartidoIdAndPersonaId(partidoId, personaId)
                .orElseThrow(() -> new ResourceNotFoundException("El jugador no esta en la alineacion."));
        partidoJugadorRepo.delete(pj);
    }

    @Transactional
    public void agregarTodosJugadores(UUID partidoId, UUID equipoTorneoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("No se puede modificar la alineacion de un partido cerrado.");
        }
        var equipoTorneo = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));
        var miembros = jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId);
        for (var je : miembros) {
            if (je.getEstado() != EstadoMembresia.ACTIVO) continue;
            var existente = partidoJugadorRepo.findByPartidoIdAndPersonaId(partidoId, je.getPersonaId());
            if (existente.isEmpty()) {
                partidoJugadorRepo.save(PartidoJugador.builder()
                        .partido(partido)
                        .personaId(je.getPersonaId())
                        .equipoTorneo(equipoTorneo)
                        .jugo(true)
                        .goles(0)
                        .build());
            } else {
                var pj = existente.get();
                pj.setJugo(true);
                partidoJugadorRepo.save(pj);
            }
        }
    }

    // ── WalkOver / Cancelar ────────────────────────────────────

    @Transactional
    public PartidoAdminDTO declararWO(UUID partidoId, UUID ganadorEquipoTorneoId, String motivo) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("El partido ya esta cerrado.");
        }

        var ganador = equipoTorneoRepo.findById(ganadorEquipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));

        boolean esLocal = partido.getEquipoLocalTorneo().getId().equals(ganador.getId());
        boolean esVisitante = partido.getEquipoVisitanteTorneo().getId().equals(ganador.getId());
        if (!esLocal && !esVisitante) {
            throw new RuntimeException("El equipo ganador no participa en este partido.");
        }

        // Limpiar eventos previos
        eventoRepo.deleteAll(eventoRepo.findByPartidoIdOrderByOrdenAsc(partidoId));

        // Crear goles de WO: 3-0
        for (int i = 0; i < 3; i++) {
            eventoRepo.save(EventoPartido.builder()
                    .partido(partido)
                    .personaId(null)
                    .equipoTorneo(ganador)
                    .tipoEvento(TipoEvento.GOL)
                    .orden(i + 1)
                    .build());
        }

        partido.setEstado(EstadoPartido.WO);
        partidoRepo.save(partido);

        limpiarAlineacion(partidoId);

        return toPartidoDTO(partido);
    }

    @Transactional
    public void cancelarPartido(UUID partidoId, String motivo) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("No se puede cancelar un partido ya cerrado.");
        }
        partido.setEstado(EstadoPartido.APLAZADO);
        partidoRepo.save(partido);
    }

    @Transactional
    public PartidoAdminDTO cerrarSinPagoArbitraje(UUID partidoId, String motivo, UUID equipoNoPagoTorneoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("El partido ya esta cerrado.");
        }

        // Limpiar eventos previos y alineacion
        eventoRepo.deleteAll(eventoRepo.findByPartidoIdOrderByOrdenAsc(partidoId));
        limpiarAlineacion(partidoId);

        partido.setEstado(EstadoPartido.FINALIZADO);
        partido.setTipoCierre("SIN_PAGO_ARBITRAJE");
        partidoRepo.save(partido);

        log.warn("Partido {} cerrado SIN_PAGO_ARBITRAJE. motivo={}, equipoNoPago={}",
                partidoId, motivo, equipoNoPagoTorneoId);

        return toPartidoDTO(partido);
    }

    @Transactional
    public PartidoAdminDTO reabrirPartido(UUID partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() != EstadoPartido.FINALIZADO) {
            throw new RuntimeException("Solo se puede reabrir un partido en estado FINALIZADO.");
        }
        partido.setEstado(EstadoPartido.EN_CURSO);
        partidoRepo.save(partido);
        return toPartidoDTO(partido);
    }

    // ── Privados ───────────────────────────────────────────────

    private void asegurarParticipantes(Partido partido, EquipoTorneo equipoTorneo) {
        var miembros = jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneo.getId());
        for (var je : miembros) {
            if (je.getEstado() != EstadoMembresia.ACTIVO) continue;
            var existente = partidoJugadorRepo.findByPartidoIdAndPersonaId(partido.getId(), je.getPersonaId());
            if (existente.isEmpty()) {
                partidoJugadorRepo.save(PartidoJugador.builder()
                        .partido(partido)
                        .personaId(je.getPersonaId())
                        .equipoTorneo(equipoTorneo)
                        .jugo(true)
                        .goles(0)
                        .build());
            }
        }
    }

    private void limpiarAlineacion(UUID partidoId) {
        var alineacion = partidoJugadorRepo.findByPartidoId(partidoId);
        partidoJugadorRepo.deleteAll(alineacion);
    }

    private PartidoJugadorDTO toPartidoJugadorDTO(PartidoJugador pj) {
        var persona = directorioPersonas.buscar(pj.getPersonaId()).orElse(null);
        return PartidoJugadorDTO.builder()
                .id(pj.getId())
                .personaId(pj.getPersonaId())
                .numeroDocumento(persona != null ? persona.numeroDocumento() : null)
                .jugadorNombre(nombreEnTorneo(persona, pj.getPersonaId(),
                        pj.getEquipoTorneo().getTorneo().getId()))
                .equipoTorneoId(pj.getEquipoTorneo().getId())
                .equipoNombre(pj.getEquipoTorneo().getEquipo().getNombre())
                .goles(pj.getGoles())
                .jugo(pj.getJugo())
                .build();
    }

    public PartidoAdminDTO toPartidoDTO(Partido p) {
        var local = p.getEquipoLocalTorneo();
        var visitante = p.getEquipoVisitanteTorneo();
        long golesLocal = local == null ? 0 : eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), local.getId(), TipoEvento.GOL);
        long golesVisitante = visitante == null ? 0 : eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), visitante.getId(), TipoEvento.GOL);
        return PartidoAdminDTO.builder()
                .id(p.getId())
                .fecha(p.getFecha())
                .estado(p.getEstado().name())
                .localEquipoTorneoId(local != null ? local.getId() : null)
                .localNombre(local != null ? local.getEquipo().getNombre() : null)
                .visitanteEquipoTorneoId(visitante != null ? visitante.getId() : null)
                .visitanteNombre(visitante != null ? visitante.getEquipo().getNombre() : null)
                .golesLocal((int) golesLocal)
                .golesVisitante((int) golesVisitante)
                .fase(p.getFase() != null ? p.getFase().name() : null)
                .jornada(p.getJornada())
                .grupo(p.getGrupo())
                .torneoId(p.getTorneo() != null ? p.getTorneo().getId() : null)
                .lugar(p.getLugar())
                .arbitros(arbitrosDe(p))
                .build();
    }

    private java.util.List<terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroAsignadoDTO> arbitrosDe(Partido p) {
        if (p.getId() == null) return java.util.List.of();
        var asignados = partidoArbitroRepo.findByPartidoIdOrderByAsignadoEnAsc(p.getId());
        if (asignados.isEmpty()) return java.util.List.of();
        var nombres = directorioPersonas.nombresDeCuentas(asignados.stream()
                .map(terminus.co.edu.ufps.codecup.competicion.model.PartidoArbitro::getCuentaId).toList());
        return asignados.stream()
                .map(pa -> new terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroAsignadoDTO(
                        pa.getCuentaId(), nombres.get(pa.getCuentaId()), pa.getAsignadoEn()))
                .toList();
    }

    private EventoDTO toEventoDTO(EventoPartido e) {
        return EventoDTO.builder()
                .id(e.getId())
                .orden(e.getOrden())
                .personaId(e.getPersonaId())
                .jugadorNombre(resolverNombreReal(e))
                .equipoTorneoId(e.getEquipoTorneo().getId())
                .equipoNombre(e.getEquipoTorneo().getEquipo().getNombre())
                .tipoEvento(e.getTipoEvento().name())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private String resolverNombreReal(EventoPartido e) {
        if (e.getPersonaId() == null) return null; // goles de W.O.
        return nombreEnTorneo(directorioPersonas.buscar(e.getPersonaId()).orElse(null),
                e.getPersonaId(), e.getEquipoTorneo().getTorneo().getId());
    }

    /**
     * Spec 0018: el nombre registrado gana, porque lo decide la persona. Si no tiene
     * cuenta, el snapshot del torneo (lo que escribio el delegado al agregarlo); y si
     * tampoco hay snapshot, el nombre de la persona (spec 0022).
     */
    private String nombreEnTorneo(PersonaResumen persona, UUID personaId, UUID torneoId) {
        if (persona != null && persona.tieneCuenta()) return persona.nombreCompleto();
        return jugadorEquipoRepo
                .findByPersonaIdAndTorneoId(personaId, torneoId)
                .map(JugadorEquipo::getNombreSnapshot)
                .filter(s -> s != null && !s.isBlank())
                .orElse(persona != null ? persona.nombreCompleto() : null);
    }
}

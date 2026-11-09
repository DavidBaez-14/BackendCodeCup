package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.dto.TorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.CrearTorneoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.GuardarConfigRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.TorneoConfigDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FormatoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import terminus.co.edu.ufps.codecup.competicion.model.*;
import terminus.co.edu.ufps.codecup.competicion.repository.TituloRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TorneoAdminService {

    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final PartidoRepository partidoRepo;
    private final PartidoJugadorRepository partidoJugadorRepo;
    private final EventoPartidoRepository eventoPartidoRepo;
    private final TituloRepository tituloRepo;
    private final ComprobanteRepository comprobanteRepo;
    private final DirectorioPersonas directorioPersonas;
    private final RolesTorneoService rolesTorneoService;
    private final PermisosTorneo permisos;
    private final terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository partidoArbitroRepo;

    /** Spec 0024: quien lo crea queda como su admin (sin rol global). */
    @Transactional
    public TorneoDTO crear(CrearTorneoRequest req, UUID creadorCuentaId) {
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del torneo es obligatorio.");
        }
        if (req.getEdicion() == null) {
            throw new RuntimeException("La edicion es obligatoria.");
        }
        var torneo = Torneo.builder()
                .nombre(req.getNombre().trim())
                .edicion(req.getEdicion())
                .estado(EstadoTorneo.BORRADOR)
                .fechaInicio(req.getFechaInicio())
                .fechaFin(req.getFechaFin())
                .repechaje(false)
                .exigeDocumento(Boolean.TRUE.equals(req.getExigeDocumento()))
                .build();
        torneoRepo.save(torneo);
        rolesTorneoService.registrarCreador(torneo, creadorCuentaId);
        return toDTO(torneo);
    }

    /** Specs 0024 y 0025: los torneos donde la cuenta es admin o árbitro, los más nuevos primero. */
    @Transactional(readOnly = true)
    public List<TorneoDTO> listarDeStaff(UUID cuentaId) {
        return torneoRepo.findAllById(permisos.torneosDondeEsStaff(cuentaId)).stream()
                .sorted(java.util.Comparator.comparing(Torneo::getEdicion, java.util.Comparator.reverseOrder())
                        .thenComparing(Torneo::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public TorneoDTO publicar(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.BORRADOR) {
            throw new RuntimeException("Solo se puede publicar un torneo en estado BORRADOR.");
        }
        if (torneo.getFormato() == null) {
            throw new RuntimeException("El torneo no tiene formato configurado. Configuralo antes de publicar.");
        }
        torneo.setEstado(EstadoTorneo.PUBLICADO);
        torneo.setPublicadoEn(LocalDateTime.now());
        torneoRepo.save(torneo);
        return toDTO(torneo);
    }

    @Transactional
    public TorneoDTO iniciar(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO) {
            throw new RuntimeException("Solo se puede iniciar un torneo en estado PUBLICADO.");
        }
        torneo.setEstado(EstadoTorneo.EN_CURSO);
        torneoRepo.save(torneo);
        return toDTO(torneo);
    }

    /** Spec 0023: el admin decide si su torneo exige documento. Se puede cambiar en cualquier estado. */
    @Transactional
    public TorneoDTO cambiarExigeDocumento(UUID torneoId, boolean exigeDocumento) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        torneo.setExigeDocumento(exigeDocumento);
        torneoRepo.save(torneo);
        return toDTO(torneo);
    }

    // ── Configuracion de formato ──────────────────────────────────

    @Transactional(readOnly = true)
    public TorneoConfigDTO obtenerConfiguracion(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        return toConfigDTO(torneo);
    }

    @Transactional
    public TorneoConfigDTO guardarConfiguracion(UUID torneoId, GuardarConfigRequest req) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        var bloqueo = motivoBloqueoFormato(torneo);
        if (bloqueo != null) {
            throw new RuntimeException(bloqueo);
        }

        FormatoTorneo formato = parseFormato(req.getFormato());
        boolean repechaje = Boolean.TRUE.equals(req.getRepechaje());
        Integer numGrupos = req.getNumGrupos();
        Integer clasifican = req.getClasificanPorGrupo();
        List<String> rondas = req.getRondasPlayoff() == null ? List.of() : req.getRondasPlayoff();

        // Validaciones por formato
        switch (formato) {
            case LIGA -> {
                numGrupos = null;
                clasifican = null;
                repechaje = false;
                rondas = List.of();
            }
            case ELIMINACION_DIRECTA -> {
                numGrupos = null;
                clasifican = null;
                repechaje = false;
                if (rondas.isEmpty()) {
                    throw new RuntimeException("Eliminacion directa requiere al menos una ronda (FINAL).");
                }
                validarRondas(rondas);
            }
            case GRUPOS_ELIMINATORIAS -> {
                if (numGrupos == null || numGrupos < 2 || numGrupos > 4) {
                    throw new RuntimeException("Grupos+Eliminatorias requiere entre 2 y 4 grupos.");
                }
                if (clasifican == null || clasifican < 1) {
                    throw new RuntimeException("Define cuantos equipos clasifican por grupo.");
                }
                repechaje = false;
                if (rondas.isEmpty()) {
                    throw new RuntimeException("Grupos+Eliminatorias requiere al menos una ronda eliminatoria.");
                }
                validarRondas(rondas);
            }
            case CHAMPIONS -> {
                if (numGrupos == null || numGrupos < 2 || numGrupos > 4) {
                    throw new RuntimeException("Champions requiere entre 2 y 4 grupos.");
                }
                if (clasifican == null || clasifican < 1) {
                    throw new RuntimeException("Define cuantos equipos clasifican por grupo.");
                }
                if (rondas.isEmpty()) {
                    throw new RuntimeException("Champions requiere al menos una ronda eliminatoria.");
                }
                validarRondas(rondas);
            }
        }

        torneo.setFormato(formato);
        torneo.setNumGrupos(numGrupos);
        torneo.setClasificanPorGrupo(clasifican);
        torneo.setRepechaje(repechaje);
        torneo.setRondasPlayoff(rondas.isEmpty() ? null : String.join(",", rondas));
        torneo.setConfiguradoEn(LocalDateTime.now());
        torneoRepo.save(torneo);
        return toConfigDTO(torneo);
    }

    @Transactional
    public void borrarFixture(UUID torneoId) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (partidoRepo.existsByTorneoIdAndEstado(torneoId, EstadoPartido.FINALIZADO)
                || partidoRepo.existsByTorneoIdAndEstado(torneoId, EstadoPartido.WO)
                || partidoRepo.existsByTorneoIdAndEstado(torneoId, EstadoPartido.EN_CURSO)) {
            throw new RuntimeException(
                "No se puede borrar el fixture: ya hay partidos jugados o en curso.");
        }
        var partidos = partidoRepo.findByTorneoId(torneoId);

        // 1) Limpiar tablas hijas: eventos del partido y alineacion (partido_jugador).
        //    Si no se borran primero, los FK *_partido_id_fkey impiden borrar partidos.
        //    Aunque el guard de arriba bloquea partidos jugados, en PROGRAMADO/APLAZADO/DESCANSO
        //    si pueden existir alineaciones precargadas por el admin o quedar resagos al expulsar
        //    un equipo (HU16), que tambien hay que limpiar.
        for (Partido p : partidos) {
            var eventos = eventoPartidoRepo.findByPartidoIdOrderByOrdenAsc(p.getId());
            if (!eventos.isEmpty()) eventoPartidoRepo.deleteAll(eventos);
            var alineacion = partidoJugadorRepo.findByPartidoId(p.getId());
            if (!alineacion.isEmpty()) partidoJugadorRepo.deleteAll(alineacion);
        }

        // 1b) Spec 0025: las asignaciones de árbitros de esos partidos.
        var asignaciones = partidoArbitroRepo.findByPartidoIdIn(partidos.stream().map(Partido::getId).toList());
        if (!asignaciones.isEmpty()) partidoArbitroRepo.deleteAll(asignaciones);

        // 2) Romper auto-referencias del bracket antes de borrar (siguiente_partido_id).
        for (Partido p : partidos) {
            p.setSiguientePartido(null);
            p.setSiguienteSlot(null);
        }
        partidoRepo.saveAll(partidos);

        // 3) Borrar los partidos.
        partidoRepo.deleteAll(partidos);
    }

    // ── Inscripciones (sin cambios) ──────────────────────────────

    @Transactional(readOnly = true)
    public List<InscripcionDTO> listarInscripciones(UUID torneoId) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        return equipoTorneoRepo.findByTorneoIdOrderByFechaInscripcionAsc(torneoId)
                .stream()
                .map(this::toInscripcionDTO)
                .toList();
    }

    @Transactional
    public InscripcionDTO aprobarInscripcion(UUID torneoId, UUID equipoTorneoId, UUID adminCuentaId) {
        var et = cargarInscripcion(torneoId, equipoTorneoId);
        if (et.getEstadoInscripcion() == EstadoInscripcion.APROBADO) {
            return toInscripcionDTO(et);
        }
        if (et.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) {
            throw new RuntimeException("La inscripcion fue rechazada y no puede aprobarse. Usa 'Habilitar' para reabrirla.");
        }
        if (et.getEstadoInscripcion() == EstadoInscripcion.EXPULSADO) {
            throw new RuntimeException("El equipo fue descalificado del torneo y no puede aprobarse.");
        }
        et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
        et.setAprobadoPorCuentaId(adminCuentaId);
        et.setMotivoRechazo(null);
        equipoTorneoRepo.save(et);
        resolverComprobanteInscripcion(equipoTorneoId, EstadoComprobante.APROBADO, null, adminCuentaId);
        return toInscripcionDTO(et);
    }

    /**
     * Resuelve el comprobante PENDIENTE_REVISION de una inscripcion cuando el admin la
     * aprueba o rechaza desde el panel de torneo, para que no quede colgado "En revision".
     */
    private void resolverComprobanteInscripcion(UUID equipoTorneoId, EstadoComprobante nuevoEstado,
                                                String motivo, UUID adminCuentaId) {
        comprobanteRepo.findByReferenciaId(equipoTorneoId).stream()
                .filter(c -> c.getEstado() == EstadoComprobante.PENDIENTE_REVISION)
                .forEach(c -> {
                    c.setEstado(nuevoEstado);
                    c.setResueltoPorCuentaId(adminCuentaId);
                    if (nuevoEstado == EstadoComprobante.RECHAZADO) {
                        c.setMotivoRechazo(motivo);
                    }
                    c.setFechaResolucion(LocalDateTime.now());
                    comprobanteRepo.save(c);
                });
    }

    @Transactional
    public InscripcionDTO rechazarInscripcion(UUID torneoId, UUID equipoTorneoId, String motivo) {
        var et = cargarInscripcion(torneoId, equipoTorneoId);
        if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
            if (et.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) {
                return toInscripcionDTO(et);
            }
            throw new RuntimeException("Solo se puede rechazar una inscripcion en estado PENDIENTE_PAGO. Para un equipo ya aprobado usa 'Descalificar'.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("El motivo del rechazo es obligatorio.");
        }
        et.setEstadoInscripcion(EstadoInscripcion.RECHAZADO);
        et.setMotivoRechazo(motivo.trim());
        equipoTorneoRepo.save(et);
        resolverComprobanteInscripcion(equipoTorneoId, EstadoComprobante.RECHAZADO, et.getMotivoRechazo(), null);
        return toInscripcionDTO(et);
    }

    @Transactional
    public InscripcionDTO habilitarInscripcion(UUID torneoId, UUID equipoTorneoId) {
        var et = cargarInscripcion(torneoId, equipoTorneoId);
        if (et.getEstadoInscripcion() != EstadoInscripcion.RECHAZADO) {
            throw new RuntimeException("Solo se puede habilitar una inscripcion en estado RECHAZADO.");
        }
        et.setEstadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        et.setMotivoRechazo(null);
        equipoTorneoRepo.save(et);
        return toInscripcionDTO(et);
    }

    @Transactional
    public InscripcionDTO expulsarEquipo(UUID torneoId, UUID equipoTorneoId, String motivo, UUID adminCuentaId) {
        var et = cargarInscripcion(torneoId, equipoTorneoId);
        if (et.getEstadoInscripcion() == EstadoInscripcion.EXPULSADO) {
            return toInscripcionDTO(et);
        }
        if (et.getEstadoInscripcion() != EstadoInscripcion.APROBADO) {
            throw new RuntimeException("Solo se puede descalificar un equipo en estado APROBADO.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("El motivo de la descalificación es obligatorio.");
        }
        et.setEstadoInscripcion(EstadoInscripcion.EXPULSADO);
        et.setExpulsadoPorCuentaId(adminCuentaId);
        et.setFechaExpulsion(LocalDateTime.now());
        et.setMotivoExpulsion(motivo.trim());
        equipoTorneoRepo.save(et);

        List<Partido> pendientes = partidoRepo.findByEquipoTorneoYEstadoIn(
                torneoId, equipoTorneoId,
                List.of(EstadoPartido.PROGRAMADO, EstadoPartido.APLAZADO));
        for (Partido p : pendientes) {
            p.setEstado(EstadoPartido.DESCANSO);
            partidoRepo.save(p);
        }

        return toInscripcionDTO(et);
    }

    private EquipoTorneo cargarInscripcion(UUID torneoId, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getTorneo().getId().equals(torneoId)) {
            throw new RuntimeException("La inscripcion no pertenece a ese torneo.");
        }
        return et;
    }

    // ── Cierre de torneo ─────────────────────────────────────────

    @Transactional
    public TorneoDTO cerrarTorneo(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.EN_CURSO) {
            throw new RuntimeException("Solo se puede cerrar un torneo en estado EN_CURSO.");
        }

        var partidos = partidoRepo.findByTorneoId(torneoId);
        for (Partido p : partidos) {
            EstadoPartido e = p.getEstado();
            if (e != EstadoPartido.FINALIZADO && e != EstadoPartido.WO
                    && e != EstadoPartido.DESCANSO) {
                throw new RuntimeException("No se puede cerrar el torneo: el partido " + p.getId()
                        + " aun no esta en estado terminal (" + e + ").");
            }
        }

        torneo.setEstado(EstadoTorneo.FINALIZADO);
        torneoRepo.save(torneo);

        // Poblar titulos deportivos (CAMPEON, SUBCAMPEON, TERCERO)
        poblarTitulos(torneo, partidos);

        log.info("[TORNEO] Torneo {} cerrado. Titulos deportivos registrados.", torneoId);
        return toDTO(torneo);
    }

    private void poblarTitulos(Torneo torneo, List<Partido> partidos) {
        var fasesKo = List.of(FaseTorneo.FINAL, FaseTorneo.TERCER_PUESTO);

        for (Partido p : partidos) {
            if (p.getEstado() != EstadoPartido.FINALIZADO
                    && p.getEstado() != EstadoPartido.WO) continue;

            if (p.getFase() == FaseTorneo.FINAL) {
                var ganador = determinarGanador(p);
                var perdedor = determinarPerdedor(p);
                if (ganador != null) {
                    guardarTitulo(torneo, ganador, Puesto.CAMPEON);
                }
                if (perdedor != null) {
                    guardarTitulo(torneo, perdedor, Puesto.SUBCAMPEON);
                }
            } else if (p.getFase() == FaseTorneo.TERCER_PUESTO) {
                var ganador = determinarGanador(p);
                if (ganador != null) {
                    guardarTitulo(torneo, ganador, Puesto.TERCERO);
                }
            }
        }
    }

    private EquipoTorneo determinarGanador(Partido p) {
        if (p.getEquipoLocalTorneo() == null || p.getEquipoVisitanteTorneo() == null) return null;
        long gLocal = eventoPartidoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), p.getEquipoLocalTorneo().getId(), TipoEvento.GOL);
        long gVis = eventoPartidoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), p.getEquipoVisitanteTorneo().getId(), TipoEvento.GOL);
        if (gLocal > gVis) return p.getEquipoLocalTorneo();
        if (gVis > gLocal) return p.getEquipoVisitanteTorneo();
        return null;
    }

    private EquipoTorneo determinarPerdedor(Partido p) {
        if (p.getEquipoLocalTorneo() == null || p.getEquipoVisitanteTorneo() == null) return null;
        long gLocal = eventoPartidoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), p.getEquipoLocalTorneo().getId(), TipoEvento.GOL);
        long gVis = eventoPartidoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), p.getEquipoVisitanteTorneo().getId(), TipoEvento.GOL);
        if (gLocal < gVis) return p.getEquipoLocalTorneo();
        if (gVis < gLocal) return p.getEquipoVisitanteTorneo();
        return null;
    }

    private void guardarTitulo(Torneo torneo, EquipoTorneo et, Puesto puesto) {
        if (et == null) return;
        tituloRepo.save(Titulo.builder()
                .torneo(torneo)
                .equipoTorneo(et)
                .puesto(puesto)
                .fecha(LocalDate.now())
                .build());
    }

    // ── Validaciones de bloqueo y mapeos ──────────────────────────

    private String motivoBloqueoFormato(Torneo t) {
        if (t.getEstado() == EstadoTorneo.FINALIZADO) {
            return "El torneo esta FINALIZADO; el formato es inmutable.";
        }
        // El formato sigue editable mientras NO existan partidos. Asi un admin que
        // descubre un error de configuracion al intentar generar el fixture puede
        // volver atras y corregirlo. En cuanto hay un solo partido en BD, el formato
        // queda bloqueado para evitar inconsistencias.
        if (partidoRepo.existsByTorneoId(t.getId())) {
            return "El fixture ya fue generado. Borralo antes de cambiar el formato.";
        }
        return null;
    }

    private String motivoBloqueoFixture(Torneo t) {
        if (t.getEstado() == EstadoTorneo.FINALIZADO) {
            return "El torneo esta FINALIZADO.";
        }
        if (partidoRepo.existsByTorneoIdAndEstado(t.getId(), EstadoPartido.FINALIZADO)
                || partidoRepo.existsByTorneoIdAndEstado(t.getId(), EstadoPartido.WO)
                || partidoRepo.existsByTorneoIdAndEstado(t.getId(), EstadoPartido.EN_CURSO)) {
            return "Hay partidos jugados o en curso; regenerar borraria su historial.";
        }
        return null;
    }

    private FormatoTorneo parseFormato(String s) {
        if (s == null || s.isBlank()) {
            throw new RuntimeException("El formato es obligatorio.");
        }
        try {
            return FormatoTorneo.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Formato invalido: " + s);
        }
    }

    private void validarRondas(List<String> rondas) {
        for (String r : rondas) {
            try {
                FaseTorneo f = FaseTorneo.valueOf(r);
                if (f == FaseTorneo.GRUPOS) {
                    throw new RuntimeException("GRUPOS no es una ronda eliminatoria valida.");
                }
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Ronda invalida: " + r);
            }
        }
    }

    public TorneoConfigDTO toConfigDTO(Torneo t) {
        int aprobados = equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                t.getId(), EstadoInscripcion.APROBADO).size();
        boolean fixture = partidoRepo.existsByTorneoId(t.getId());
        boolean jugado = partidoRepo.existsByTorneoIdAndEstado(t.getId(), EstadoPartido.FINALIZADO)
                || partidoRepo.existsByTorneoIdAndEstado(t.getId(), EstadoPartido.WO)
                || partidoRepo.existsByTorneoIdAndEstado(t.getId(), EstadoPartido.EN_CURSO);
        String bloqFormato = motivoBloqueoFormato(t);
        String bloqFixture = motivoBloqueoFixture(t);

        List<String> rondas = (t.getRondasPlayoff() == null || t.getRondasPlayoff().isBlank())
                ? List.of()
                : Arrays.stream(t.getRondasPlayoff().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        return TorneoConfigDTO.builder()
                .torneoId(t.getId())
                .torneoNombre(t.getNombre())
                .estado(t.getEstado() != null ? t.getEstado().name() : null)
                .formato(t.getFormato() != null ? t.getFormato().name() : null)
                .exigeDocumento(Boolean.TRUE.equals(t.getExigeDocumento()))
                .numGrupos(t.getNumGrupos())
                .clasificanPorGrupo(t.getClasificanPorGrupo())
                .repechaje(t.getRepechaje())
                .rondasPlayoff(rondas)
                .equiposAprobados(aprobados)
                .fixtureGenerado(fixture)
                .hayPartidoJugado(jugado)
                .formatoEditable(bloqFormato == null)
                .fixtureRegenerable(bloqFixture == null)
                .motivoBloqueoFormato(bloqFormato)
                .motivoBloqueoFixture(bloqFixture)
                .build();
    }

    public InscripcionDTO toInscripcionDTO(EquipoTorneo et) {
        return InscripcionDTO.builder()
                .id(et.getId())
                .torneoId(et.getTorneo().getId())
                .torneoNombre(et.getTorneo().getNombre())
                .equipoId(et.getEquipo().getId())
                .equipoNombre(et.getEquipo().getNombre())
                .delegadoPersonaId(et.getDelegadoPersonaId())
                .delegadoNombre(directorioPersonas.buscar(et.getDelegadoPersonaId())
                        .map(PersonaResumen::nombreCompleto).orElse(null))
                .estadoInscripcion(et.getEstadoInscripcion().name())
                .aprobadoPor(directorioPersonas.nombreDeCuenta(et.getAprobadoPorCuentaId()))
                .motivoRechazo(et.getMotivoRechazo())
                .fechaInscripcion(et.getFechaInscripcion())
                .expulsadoPor(directorioPersonas.nombreDeCuenta(et.getExpulsadoPorCuentaId()))
                .fechaExpulsion(et.getFechaExpulsion())
                .motivoExpulsion(et.getMotivoExpulsion())
                .montoInscripcion(et.getTorneo().getMontoInscripcion())
                .build();
    }

    public TorneoDTO toDTO(Torneo t) {
        return TorneoDTO.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .edicion(t.getEdicion())
                .estado(t.getEstado() != null ? t.getEstado().name() : null)
                .fechaInicio(t.getFechaInicio())
                .fechaFin(t.getFechaFin())
                .publicadoEn(t.getPublicadoEn())
                .formato(t.getFormato() != null ? t.getFormato().name() : null)
                .exigeDocumento(Boolean.TRUE.equals(t.getExigeDocumento()))
                .build();
    }
}

package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.competicion.dto.SolicitudDTO;
import terminus.co.edu.ufps.codecup.competicion.model.*;
import terminus.co.edu.ufps.codecup.competicion.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudEquipoRepository solicitudRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final JugadorRepository jugadorRepo;
    private final DirectorioPersonas directorioPersonas;

    // ──────────────────────────────────────────────
    //  JUGADOR: crear solicitud de ingreso (con datos deportivos)
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO crear(String cedula, UUID equipoTorneoId) {
        if (equipoTorneoId == null) {
            throw new RuntimeException("equipoTorneoId es obligatorio.");
        }

        var equipoTorneo = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));
        if (equipoTorneo.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) {
            throw new RuntimeException("Ese equipo fue rechazado en el torneo; no puedes unirte.");
        }
        var torneo = equipoTorneo.getTorneo();
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO && torneo.getEstado() != EstadoTorneo.EN_CURSO) {
            throw new RuntimeException("El torneo no esta aceptando jugadores.");
        }

        // El perfil deportivo (altura/pierna/posicion) es opcional (spec 0020):
        // no se exige para pedir ingreso. Al aprobar se copia lo que haya.
        var jugador = jugadorRepo.findById(cedula).orElseGet(() -> {
            var persona = directorioPersonas.buscarPorDocumento(cedula)
                    .orElseThrow(() -> new RuntimeException(
                            "La cedula " + cedula + " no corresponde a ninguna persona registrada en CodeCup."));
            return jugadorRepo.save(Jugador.builder()
                    .cedula(cedula)
                    .nombre(persona.nombreCompleto())
                    .correo(persona.correo())
                    .build());
        });

        // Validaciones de negocio
        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(cedula, torneo.getId())) {
            throw new RuntimeException("Ya perteneces a un equipo en este torneo.");
        }
        if (solicitudRepo.existsByCedulaAndTorneoIdAndEstadoIn(
                cedula, torneo.getId(), List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA))) {
            throw new RuntimeException("Ya tienes una solicitud activa para este torneo.");
        }

        // nombre y correo son NOT NULL en la tabla; se snapshotean del Jugador
        // local (que ya los tomo de la persona registrada al crearlo arriba).
        var solicitud = SolicitudEquipo.builder()
                .cedula(cedula)
                .nombre(jugador.getNombre())
                .correo(jugador.getCorreo())
                .torneo(torneo)
                .equipoTorneo(equipoTorneo)
                .build();
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesJugador(String cedula) {
        return solicitudRepo.findByCedula(cedula).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesDelegado(String cedulaDelegado) {
        var inscripciones = equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(cedulaDelegado);
        if (inscripciones.isEmpty()) return List.of();
        var ids = inscripciones.stream().map(EquipoTorneo::getId).toList();
        return solicitudRepo.findByEquipoTorneoIdIn(ids).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarPendientesDelegado(String cedulaDelegado) {
        var inscripciones = equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(cedulaDelegado);
        if (inscripciones.isEmpty()) return List.of();
        var ids = inscripciones.stream().map(EquipoTorneo::getId).toList();
        return solicitudRepo.findByEquipoTorneoIdInAndEstado(ids, EstadoSolicitud.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public SolicitudDTO aprobar(UUID solicitudId, String cedulaDelegado) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        validarDelegadoEsDueno(solicitud, cedulaDelegado);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }
        // Spec 0018: si lo bloquearon despues de pedir el ingreso, no entra al plantel.
        boolean bloqueada = directorioPersonas.buscarPorDocumento(solicitud.getCedula())
                .map(PersonaResumen::cuentaBloqueada)
                .orElse(false);
        if (bloqueada) {
            throw ApiException.conflict("JUGADOR_BLOQUEADO",
                    "Esta persona tiene la cuenta bloqueada: no puede entrar a un plantel.");
        }
        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(
                solicitud.getCedula(), solicitud.getTorneo().getId())) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        // Snapshot del nombre (persona registrada) + snapshot deportivo del Jugador local.
        var je = JugadorEquipo.builder()
                .cedula(solicitud.getCedula())
                .torneo(solicitud.getTorneo())
                .equipoTorneo(solicitud.getEquipoTorneo())
                .fechaInicio(LocalDate.now())
                .estado(EstadoMembresia.ACTIVO)
                .build();
        poblarSnapshotNombre(je, solicitud.getCedula());
        poblarSnapshotDeportivo(je, solicitud.getCedula());
        jugadorEquipoRepo.save(je);

        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    @Transactional
    public SolicitudDTO rechazar(UUID solicitudId, String motivo, String cedulaDelegado) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        validarDelegadoEsDueno(solicitud, cedulaDelegado);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        solicitud.setMotivoRechazo(motivo);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    /**
     * Snapshot deportivo desde el Jugador local. El perfil es opcional
     * (spec 0020): lo que falte queda en null.
     */
    private void poblarSnapshotDeportivo(JugadorEquipo je, String cedula) {
        jugadorRepo.findById(cedula).ifPresent(j -> {
            je.setAlturaCmSnapshot(j.getAlturaCm());
            je.setPiernaHabilSnapshot(j.getPiernaHabil());
            je.setPosicionSnapshot(j.getPosicion());
        });
    }

    /**
     * Snapshot del nombre desde la persona registrada. Si la cedula no es de
     * nadie registrado, lanzamos error: nunca queremos un JugadorEquipo con
     * snapshot null porque luego se muestra la cedula en eventos/plantel en
     * lugar del nombre real. Sin padron ya no hay semestre ni rol academico.
     */
    private void poblarSnapshotNombre(JugadorEquipo je, String cedula) {
        var p = directorioPersonas.buscarPorDocumento(cedula)
                .orElseThrow(() -> new RuntimeException(
                        "La cedula " + cedula + " no corresponde a ninguna persona registrada en CodeCup."));
        je.setNombreSnapshot(p.nombreCompleto());
        je.setSnapshotAt(LocalDateTime.now());
    }

    private void validarDelegadoEsDueno(SolicitudEquipo solicitud, String cedulaDelegado) {
        var et = solicitud.getEquipoTorneo();
        if (et.getDelegadoCedula() == null || !et.getDelegadoCedula().equals(cedulaDelegado)) {
            throw new RuntimeException("No eres el delegado de este equipo.");
        }
    }

    private SolicitudDTO toDTO(SolicitudEquipo s) {
        var et = s.getEquipoTorneo();
        var jugador = jugadorRepo.findById(s.getCedula()).orElse(null);
        var persona = directorioPersonas.buscarPorDocumento(s.getCedula()).orElse(null);
        return SolicitudDTO.builder()
                .id(s.getId())
                .cedula(s.getCedula())
                .nombre(nombreDe(persona, jugador))
                .correo(persona != null ? persona.correo() : null)
                .alturaCm(jugador != null ? jugador.getAlturaCm() : null)
                .piernaHabil(jugador != null ? jugador.getPiernaHabil() : null)
                .posicion(jugador != null ? jugador.getPosicion() : null)
                .equipoTorneoId(et.getId())
                .equipoId(et.getEquipo().getId())
                .equipoNombre(et.getEquipo().getNombre())
                .torneoId(s.getTorneo().getId())
                .torneoNombre(s.getTorneo().getNombre())
                .estado(s.getEstado().name())
                .fechaSolicitud(s.getFechaSolicitud())
                .fechaResolucion(s.getFechaResolucion())
                .motivoRechazo(s.getMotivoRechazo())
                .build();
    }

    /** Prioridad: apodo cosmetico > nombre de la persona registrada. */
    private String nombreDe(PersonaResumen persona, Jugador jugador) {
        if (jugador != null && jugador.getApodo() != null && !jugador.getApodo().isBlank()) {
            return jugador.getApodo();
        }
        return persona != null ? persona.nombreCompleto() : null;
    }
}

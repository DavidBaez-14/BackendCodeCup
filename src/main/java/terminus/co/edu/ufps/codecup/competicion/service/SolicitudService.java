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

/** Solicitudes de un jugador para entrar al plantel de un equipo. Por persona desde la spec 0022. */
@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudEquipoRepository solicitudRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final PerfilDeportivoRepository perfilRepo;
    private final DirectorioPersonas directorioPersonas;

    // ──────────────────────────────────────────────
    //  JUGADOR: crear solicitud de ingreso
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO crear(UUID personaId, UUID equipoTorneoId) {
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

        // El perfil deportivo es opcional (spec 0020): no se exige para pedir ingreso.
        var persona = directorioPersonas.buscar(personaId)
                .orElseThrow(() -> new ResourceNotFoundException("Completa tu registro en CodeCup para continuar."));
        exigirDocumentoSiHaceFalta(torneo, persona,
                "Este torneo exige documento: agrégalo en tu perfil antes de pedir unirte.");

        if (jugadorEquipoRepo.existsByPersonaIdAndTorneoId(personaId, torneo.getId())) {
            throw new RuntimeException("Ya perteneces a un equipo en este torneo.");
        }
        if (solicitudRepo.existsByPersonaIdAndTorneoIdAndEstadoIn(
                personaId, torneo.getId(), List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA))) {
            throw new RuntimeException("Ya tienes una solicitud activa para este torneo.");
        }

        var solicitud = SolicitudEquipo.builder()
                .personaId(personaId)
                .torneo(torneo)
                .equipoTorneo(equipoTorneo)
                .build();
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesJugador(UUID personaId) {
        return solicitudRepo.findByPersonaId(personaId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesDelegado(UUID delegadoPersonaId) {
        var inscripciones = equipoTorneoRepo.findByDelegadoPersonaIdOrderByFechaInscripcionDesc(delegadoPersonaId);
        if (inscripciones.isEmpty()) return List.of();
        var ids = inscripciones.stream().map(EquipoTorneo::getId).toList();
        return solicitudRepo.findByEquipoTorneoIdIn(ids).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarPendientesDelegado(UUID delegadoPersonaId) {
        var inscripciones = equipoTorneoRepo.findByDelegadoPersonaIdOrderByFechaInscripcionDesc(delegadoPersonaId);
        if (inscripciones.isEmpty()) return List.of();
        var ids = inscripciones.stream().map(EquipoTorneo::getId).toList();
        return solicitudRepo.findByEquipoTorneoIdInAndEstado(ids, EstadoSolicitud.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public SolicitudDTO aprobar(UUID solicitudId, UUID delegadoPersonaId) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        validarDelegadoEsDueno(solicitud, delegadoPersonaId);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }
        var persona = directorioPersonas.requerir(solicitud.getPersonaId());
        // Spec 0018: si lo bloquearon despues de pedir el ingreso, no entra al plantel.
        if (persona.cuentaBloqueada()) {
            throw ApiException.conflict("JUGADOR_BLOQUEADO",
                    "Esta persona tiene la cuenta bloqueada: no puede entrar a un plantel.");
        }
        exigirDocumentoSiHaceFalta(solicitud.getTorneo(), persona,
                "Este torneo exige documento y esta persona todavía no lo tiene registrado.");
        if (jugadorEquipoRepo.existsByPersonaIdAndTorneoId(
                solicitud.getPersonaId(), solicitud.getTorneo().getId())) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        // Snapshot del nombre (persona registrada) + snapshot deportivo de su perfil.
        var je = JugadorEquipo.builder()
                .personaId(solicitud.getPersonaId())
                .torneo(solicitud.getTorneo())
                .equipoTorneo(solicitud.getEquipoTorneo())
                .fechaInicio(LocalDate.now())
                .estado(EstadoMembresia.ACTIVO)
                .nombreSnapshot(persona.nombreCompleto())
                .snapshotAt(LocalDateTime.now())
                .build();
        perfilRepo.findById(solicitud.getPersonaId()).ifPresent(p -> {
            je.setAlturaCmSnapshot(p.getAlturaCm());
            je.setPiernaHabilSnapshot(p.getPiernaHabil());
            je.setPosicionSnapshot(p.getPosicion());
        });
        jugadorEquipoRepo.save(je);

        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    @Transactional
    public SolicitudDTO rechazar(UUID solicitudId, String motivo, UUID delegadoPersonaId) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        validarDelegadoEsDueno(solicitud, delegadoPersonaId);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        solicitud.setMotivoRechazo(motivo);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    /** Spec 0023: si el torneo exige documento, quien entra al plantel debe tenerlo. */
    private static void exigirDocumentoSiHaceFalta(Torneo torneo, PersonaResumen persona, String mensaje) {
        if (Boolean.TRUE.equals(torneo.getExigeDocumento()) && persona.numeroDocumento() == null) {
            throw ApiException.conflict("DOCUMENTO_REQUERIDO", mensaje);
        }
    }

    private void validarDelegadoEsDueno(SolicitudEquipo solicitud, UUID delegadoPersonaId) {
        var et = solicitud.getEquipoTorneo();
        if (et.getDelegadoPersonaId() == null || !et.getDelegadoPersonaId().equals(delegadoPersonaId)) {
            throw new RuntimeException("No eres el delegado de este equipo.");
        }
    }

    private SolicitudDTO toDTO(SolicitudEquipo s) {
        var et = s.getEquipoTorneo();
        var perfil = perfilRepo.findById(s.getPersonaId()).orElse(null);
        var persona = directorioPersonas.buscar(s.getPersonaId()).orElse(null);
        return SolicitudDTO.builder()
                .id(s.getId())
                .personaId(s.getPersonaId())
                .numeroDocumento(persona != null ? persona.numeroDocumento() : null)
                .nombre(nombreDe(persona, perfil))
                .correo(persona != null ? persona.correo() : null)
                .alturaCm(perfil != null ? perfil.getAlturaCm() : null)
                .piernaHabil(perfil != null ? perfil.getPiernaHabil() : null)
                .posicion(perfil != null ? perfil.getPosicion() : null)
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

    /** Prioridad: apodo cosmetico > nombre de la persona. */
    private static String nombreDe(PersonaResumen persona, PerfilDeportivo perfil) {
        if (perfil != null && perfil.apodoVisible() != null) {
            return perfil.apodoVisible();
        }
        return persona != null ? persona.nombreCompleto() : null;
    }
}

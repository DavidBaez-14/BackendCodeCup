package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.VinculoPendienteDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.PerfilDeportivo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PerfilDeportivoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PremioAsignadoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.SolicitudEquipoRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.VinculosPersona;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Vinculación verificada desde la competición (spec 0023, D1). Identidad guarda la solicitud y hace
 * el cambio de persona; aquí se decide quién puede aprobarla (el delegado de un equipo donde estuvo
 * la persona, o un admin) y se comprueba que la persona actual de la cuenta no tenga historial.
 */
@Service
@RequiredArgsConstructor
public class VinculacionService {

    private final VinculosPersona vinculosPersona;
    private final DirectorioPersonas directorioPersonas;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final SolicitudEquipoRepository solicitudRepo;
    private final PartidoJugadorRepository partidoJugadorRepo;
    private final EventoPartidoRepository eventoRepo;
    private final PremioAsignadoRepository premioAsignadoRepo;
    private final PerfilDeportivoRepository perfilRepo;
    private final MultaRepository multaRepo;

    /** El admin ve todas; un delegado, solo las de personas que estuvieron en alguno de sus planteles. */
    @Transactional(readOnly = true)
    public List<VinculoPendienteDTO> pendientes(UUID personaQueConsulta, boolean esAdmin) {
        var solicitudes = esAdmin
                ? vinculosPersona.pendientes()
                : vinculosPersona.pendientesHacia(personasEnPlantelesDe(personaQueConsulta));
        return solicitudes.stream().map(this::toDTO).toList();
    }

    @Transactional
    public void aprobar(UUID vinculoId, UUID personaQueAprueba, UUID cuentaQueAprueba, boolean esAdmin) {
        var vinculo = vinculosPersona.requerirPendiente(vinculoId);
        exigirPermiso(vinculo.personaId(), personaQueAprueba, esAdmin);

        // Sin fusiones en la v1: la persona actual de la cuenta no puede tener historial.
        UUID anterior = vinculo.personaActualId();
        if (anterior != null && tieneParticipaciones(anterior)) {
            throw ApiException.conflict("PERSONA_CON_PARTICIPACIONES",
                    "La cuenta ya tiene participaciones propias: no se puede vincular a otra persona.");
        }
        if (anterior != null) {
            moverPerfil(anterior, vinculo.personaId());
        }
        vinculosPersona.aprobar(vinculoId, cuentaQueAprueba);
    }

    @Transactional
    public void rechazar(UUID vinculoId, String motivo, UUID personaQueRechaza, UUID cuentaQueRechaza, boolean esAdmin) {
        var vinculo = vinculosPersona.requerirPendiente(vinculoId);
        exigirPermiso(vinculo.personaId(), personaQueRechaza, esAdmin);
        vinculosPersona.rechazar(vinculoId, cuentaQueRechaza, motivo);
    }

    /** Plantel, solicitudes, equipo o inscripción como delegado, partidos, eventos, premios o multas. */
    boolean tieneParticipaciones(UUID personaId) {
        return jugadorEquipoRepo.existsByPersonaId(personaId)
                || solicitudRepo.existsByPersonaId(personaId)
                || equipoRepo.existsByDelegadoPersonaId(personaId)
                || equipoTorneoRepo.existsByDelegadoPersonaId(personaId)
                || partidoJugadorRepo.existsByPersonaId(personaId)
                || eventoRepo.existsByPersonaId(personaId)
                || premioAsignadoRepo.existsByPersonaId(personaId)
                || multaRepo.existsByPersonaId(personaId);
    }

    private void exigirPermiso(UUID personaReclamada, UUID quien, boolean esAdmin) {
        if (esAdmin) return;
        if (!personasEnPlantelesDe(quien).contains(personaReclamada)) {
            throw ApiException.forbidden("VINCULO_AJENO",
                    "Solo el delegado de un equipo donde estuvo esa persona (o un admin) puede resolver esta solicitud.");
        }
    }

    /** Personas que estuvieron en los planteles de las inscripciones donde {@code delegado} es el delegado. */
    private Set<UUID> personasEnPlantelesDe(UUID delegado) {
        var inscripciones = equipoTorneoRepo.findByDelegadoPersonaIdOrderByFechaInscripcionDesc(delegado).stream()
                .map(EquipoTorneo::getId)
                .toList();
        if (inscripciones.isEmpty()) return Set.of();
        return jugadorEquipoRepo.findByEquipoTorneoIdIn(inscripciones).stream()
                .map(JugadorEquipo::getPersonaId)
                .collect(Collectors.toSet());
    }

    /** Si la persona destino no tiene perfil deportivo y la anterior sí, se le pasa; si no, se descarta. */
    private void moverPerfil(UUID anterior, UUID destino) {
        perfilRepo.findById(anterior).ifPresent(perfil -> {
            if (perfilRepo.findById(destino).isEmpty()) {
                perfilRepo.save(PerfilDeportivo.builder()
                        .personaId(destino)
                        .alturaCm(perfil.getAlturaCm())
                        .piernaHabil(perfil.getPiernaHabil())
                        .posicion(perfil.getPosicion())
                        .apodo(perfil.getApodo())
                        .fotoUrl(perfil.getFotoUrl())
                        .build());
            }
            perfilRepo.delete(perfil);
            perfilRepo.flush();
        });
    }

    private VinculoPendienteDTO toDTO(VinculosPersona.Resumen v) {
        var persona = directorioPersonas.buscar(v.personaId()).orElse(null);
        var participaciones = jugadorEquipoRepo.findByPersonaIdOrderByFechaInicioDesc(v.personaId()).stream()
                .map(je -> je.getEquipoTorneo().getEquipo().getNombre() + " (" + je.getTorneo().getNombre() + ")")
                .distinct()
                .toList();
        return VinculoPendienteDTO.builder()
                .id(v.id())
                .creadoEn(v.creadoEn())
                .solicitanteNombre(v.solicitanteNombre())
                .solicitanteCorreo(v.solicitanteCorreo())
                .personaId(v.personaId())
                .personaNombre(persona != null ? persona.nombreCompleto() : null)
                .personaNumeroDocumento(persona != null ? persona.numeroDocumento() : null)
                .participaciones(participaciones)
                .build();
    }
}

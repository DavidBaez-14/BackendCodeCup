package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.MiembroReclamableDTO;
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
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

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
    private final PermisosTorneo permisos;

    /** El admin ve todas; un delegado, solo las de personas que estuvieron en alguno de sus planteles. */
    @Transactional(readOnly = true)
    public List<VinculoPendienteDTO> pendientes(UUID personaQueConsulta, UUID cuentaQueConsulta, boolean esSuperadmin) {
        var solicitudes = esSuperadmin
                ? vinculosPersona.pendientes()
                : vinculosPersona.pendientesHacia(personasQuePuedeResolver(personaQueConsulta, cuentaQueConsulta));
        return solicitudes.stream().map(this::toDTO).toList();
    }

    @Transactional
    public void aprobar(UUID vinculoId, UUID personaQueAprueba, UUID cuentaQueAprueba, boolean esSuperadmin) {
        var vinculo = vinculosPersona.requerirPendiente(vinculoId);
        exigirPermiso(vinculo.personaId(), personaQueAprueba, cuentaQueAprueba, esSuperadmin);

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
    public void rechazar(UUID vinculoId, String motivo, UUID personaQueRechaza, UUID cuentaQueRechaza, boolean esSuperadmin) {
        var vinculo = vinculosPersona.requerirPendiente(vinculoId);
        exigirPermiso(vinculo.personaId(), personaQueRechaza, cuentaQueRechaza, esSuperadmin);
        vinculosPersona.rechazar(vinculoId, cuentaQueRechaza, motivo);
    }

    // ─── D3: reclamar un historial anotado sin documento ────────────────────

    /**
     * Los miembros sin cuenta de un plantel, para que alguien se reconozca ("ese soy yo"). Sin
     * documento ni correo; un menor (TI) sale con su nombre visible.
     */
    @Transactional(readOnly = true)
    public List<MiembroReclamableDTO> miembrosSinCuenta(UUID equipoTorneoId) {
        equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        var plantel = jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId);
        var personas = directorioPersonas.buscarVarias(plantel.stream().map(JugadorEquipo::getPersonaId).toList());
        return plantel.stream()
                .filter(je -> personas.containsKey(je.getPersonaId()) && !personas.get(je.getPersonaId()).tieneCuenta())
                .map(je -> {
                    var p = personas.get(je.getPersonaId());
                    String nombre = p.tipoDocumento() == TipoDocumento.TI || je.getNombreSnapshot() == null
                            || je.getNombreSnapshot().isBlank()
                            ? p.nombreVisible()
                            : je.getNombreSnapshot();
                    return MiembroReclamableDTO.builder()
                            .personaId(je.getPersonaId())
                            .nombre(nombre)
                            .numeroCamiseta(je.getNumeroCamiseta())
                            .build();
                })
                .toList();
    }

    /**
     * D3 (8-nov-2026): una cuenta dice "esa persona del plantel soy yo". Abre la misma solicitud de
     * vínculo que el registro con documento; la aprueba el delegado de un equipo donde estuvo esa
     * persona o un admin.
     */
    @Transactional
    public VinculoPendienteDTO reclamar(UUID cuentaQueReclama, UUID personaQueReclama, UUID personaReclamada) {
        var objetivo = directorioPersonas.requerir(personaReclamada);
        if (objetivo.tieneCuenta()) {
            throw ApiException.conflict("PERSONA_YA_VINCULADA", "Esa persona ya tiene una cuenta.");
        }
        if (!jugadorEquipoRepo.existsByPersonaId(personaReclamada)) {
            throw ApiException.conflict("SIN_HISTORIAL", "Esa persona no está en ningún plantel: no hay historial que reclamar.");
        }
        var actual = directorioPersonas.requerir(personaQueReclama);
        if (objetivo.numeroDocumento() != null && actual.numeroDocumento() != null) {
            throw ApiException.conflict("DOCUMENTOS_DISTINTOS",
                    "Esa persona tiene otro documento registrado: no puede ser la misma que tu cuenta.");
        }
        return toDTO(vinculosPersona.solicitar(cuentaQueReclama, personaReclamada));
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

    /**
     * Spec 0024: resuelve el SUPERADMIN, el delegado de un equipo donde estuvo esa persona o un admin
     * de un torneo donde estuvo. Ya no basta con el rol global ADMINISTRADOR.
     */
    private void exigirPermiso(UUID personaReclamada, UUID quien, UUID cuentaDeQuien, boolean esSuperadmin) {
        if (esSuperadmin) return;
        if (!personasQuePuedeResolver(quien, cuentaDeQuien).contains(personaReclamada)) {
            throw ApiException.forbidden("VINCULO_AJENO",
                    "Solo el delegado de un equipo donde estuvo esa persona (o un admin de ese torneo) puede resolver esta solicitud.");
        }
    }

    /** Las de sus planteles como delegado y las de los torneos que administra. */
    private Set<UUID> personasQuePuedeResolver(UUID persona, UUID cuenta) {
        var personas = new java.util.HashSet<>(personasEnPlantelesDe(persona));
        var torneos = permisos.torneosQueAdministra(cuenta);
        if (!torneos.isEmpty()) {
            jugadorEquipoRepo.findByTorneoIdIn(torneos).forEach(je -> personas.add(je.getPersonaId()));
        }
        return personas;
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

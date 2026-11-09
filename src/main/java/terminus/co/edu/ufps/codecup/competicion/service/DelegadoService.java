package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionInfoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.AgregarMiembroRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.InscribirEquipoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.codecup.competicion.model.*;
import terminus.co.edu.ufps.codecup.competicion.repository.*;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que hace el delegado con su equipo. Desde la spec 0022 el delegado y los miembros
 * se identifican por {@code persona_id}; el documento solo sirve para encontrar o crear
 * a la persona al agregarla al plantel.
 */
@Service
@RequiredArgsConstructor
public class DelegadoService {

    private final EquipoRepository equipoRepo;
    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final PerfilDeportivoRepository perfilRepo;
    private final TorneoAdminService torneoAdminService;
    private final DirectorioPersonas directorioPersonas;
    private final ComprobanteRepository comprobanteRepo;
    private final VisibilidadService visibilidad;
    private final ReglasInscripcion reglasInscripcion;

    @Transactional(readOnly = true)
    public InscripcionInfoDTO obtenerInscripcionInfo(UUID delegadoPersonaId, UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        var et = equipoTorneoRepo.findByTorneoIdAndDelegadoPersonaId(torneoId, delegadoPersonaId)
                .orElse(null);

        String estadoComprobante = null;
        if (et != null) {
            var comprobantes = comprobanteRepo.findByReferenciaId(et.getId());
            if (!comprobantes.isEmpty()) {
                estadoComprobante = comprobantes.get(0).getEstado().name();
            }
        }

        return InscripcionInfoDTO.builder()
                .equipoTorneoId(et != null ? et.getId() : null)
                .montoInscripcion(torneo.getMontoInscripcion())
                .cuentaDestino(torneo.getCuentaDestino())
                .bancoDestino(torneo.getBancoDestino())
                .titularCuenta(torneo.getTitularCuenta())
                .estadoComprobante(estadoComprobante)
                .build();
    }

    @Transactional
    public EquipoDTO crearEquipo(UUID delegadoPersonaId, CrearEquipoRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del equipo es obligatorio.");
        }
        directorioPersonas.requerir(delegadoPersonaId);
        var equipo = nuevoEquipo(delegadoPersonaId, req.getNombre());
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    /**
     * Spec 0026 (Q1): una persona puede tener varios equipos base (por ejemplo, uno por deporte).
     * Lo que no puede es repetir el nombre entre los suyos: 409 EQUIPO_DUPLICADO.
     */
    private Equipo nuevoEquipo(UUID delegadoPersonaId, String nombreEscrito) {
        String nombre = nombreEscrito == null ? "" : nombreEscrito.trim();
        if (nombre.isEmpty()) {
            throw ApiException.badRequest("NOMBRE_INVALIDO", "El nombre del equipo es obligatorio.");
        }
        if (nombre.length() > 150) {
            throw ApiException.badRequest("NOMBRE_INVALIDO", "El nombre del equipo no puede tener más de 150 caracteres.");
        }
        boolean repetido = equipoRepo.findByDelegadoPersonaIdOrderByCreatedAtDesc(delegadoPersonaId).stream()
                .anyMatch(e -> e.getNombre().equalsIgnoreCase(nombre));
        if (repetido) {
            throw ApiException.conflict("EQUIPO_DUPLICADO", "Ya tienes un equipo llamado \"" + nombre + "\".");
        }
        return equipoRepo.save(Equipo.builder().nombre(nombre).delegadoPersonaId(delegadoPersonaId).build());
    }

    /** Compatibilidad con la web: el equipo base más reciente. 404 si no tiene ninguno. */
    @Transactional(readOnly = true)
    public EquipoDTO miEquipo(UUID delegadoPersonaId) {
        var equipo = equipoRepo.findByDelegadoPersonaIdOrderByCreatedAtDesc(delegadoPersonaId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Aun no has creado tu equipo."));
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    /** Spec 0026: todos sus equipos base, el más reciente primero. */
    @Transactional(readOnly = true)
    public List<EquipoDTO> misEquiposBase(UUID delegadoPersonaId) {
        return equipoRepo.findByDelegadoPersonaIdOrderByCreatedAtDesc(delegadoPersonaId).stream()
                .map(e -> EquipoDTO.builder().id(e.getId()).nombre(e.getNombre()).build())
                .toList();
    }

    /**
     * Qué equipo se inscribe (spec 0026): el que nombra {@code equipoId} (debe ser suyo), uno nuevo
     * con {@code nombreEquipo}, o el único que tiene. Con varios y sin elegir: 400 EQUIPO_REQUERIDO.
     */
    private Equipo equipoParaInscribir(UUID delegadoPersonaId, InscribirEquipoRequest req) {
        if (req != null && req.getNombreEquipo() != null && !req.getNombreEquipo().isBlank()) {
            return nuevoEquipo(delegadoPersonaId, req.getNombreEquipo());
        }
        if (req != null && req.getEquipoId() != null) {
            var equipo = equipoRepo.findById(req.getEquipoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado."));
            if (!equipo.getDelegadoPersonaId().equals(delegadoPersonaId)) {
                throw ApiException.forbidden("EQUIPO_AJENO", "Ese equipo no es tuyo.");
            }
            return equipo;
        }
        var propios = equipoRepo.findByDelegadoPersonaIdOrderByCreatedAtDesc(delegadoPersonaId);
        if (propios.size() == 1) return propios.get(0);
        throw ApiException.badRequest("EQUIPO_REQUERIDO", propios.isEmpty()
                ? "Escribe el nombre de tu equipo (nombreEquipo) o crea uno antes de inscribirlo."
                : "Tienes varios equipos: elige cuál inscribir (equipoId).");
    }

    @Transactional(readOnly = true)
    public List<TorneoDisponibleDTO> torneosDisponibles(UUID delegadoPersonaId) {
        var publicados = torneoRepo.findByEstadoInOrderByEdicionDesc(
                List.of(EstadoTorneo.PUBLICADO, EstadoTorneo.EN_CURSO));
        // Spec 0029: solo los públicos y aquellos donde ya participa (los NO_LISTADO y PRIVADO no se listan).
        var quien = VisibilidadService.actual();
        return publicados.stream().filter(t -> visibilidad.puedeVer(t, quien, false)).map(t -> {
            var inscripcion = equipoTorneoRepo.findByTorneoIdAndDelegadoPersonaId(t.getId(), delegadoPersonaId);
            return TorneoDisponibleDTO.builder()
                    .id(t.getId())
                    .nombre(t.getNombre())
                    .edicion(t.getEdicion())
                    .estado(t.getEstado().name())
                    .fechaInicio(t.getFechaInicio())
                    .fechaFin(t.getFechaFin())
                    .inscrito(inscripcion.isPresent())
                    .estadoInscripcion(inscripcion.map(et -> et.getEstadoInscripcion().name()).orElse(null))
                    .equipoTorneoId(inscripcion.map(EquipoTorneo::getId).orElse(null))
                    .motivoExpulsion(inscripcion.map(EquipoTorneo::getMotivoExpulsion).orElse(null))
                    .build();
        }).toList();
    }

    /** Compatibilidad: inscribe su único equipo (o falla con EQUIPO_REQUERIDO si tiene varios). */
    @Transactional
    public InscripcionDTO inscribir(UUID delegadoPersonaId, UUID torneoId) {
        return inscribir(delegadoPersonaId, torneoId, null);
    }

    /**
     * Spec 0026: cualquier cuenta inscribe un equipo en un torneo PUBLICADO, sin rol global. Queda
     * PENDIENTE_PAGO, con ella como delegado y en el plantel; la aprobación del admin decide.
     */
    @Transactional
    public InscripcionDTO inscribir(UUID delegadoPersonaId, UUID torneoId, InscribirEquipoRequest req) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        // Spec 0029: un NO_LISTADO se inscribe con su enlace; un PRIVADO, solo por invitación.
        visibilidad.exigirVisible(torneo, VisibilidadService.actual(),
                VisibilidadService.conEnlace(torneo, req != null ? req.getEnlaceToken() : null));
        reglasInscripcion.exigirAbiertas(torneo);
        reglasInscripcion.exigirPorEquipos(torneo);
        reglasInscripcion.exigirCupo(torneo);
        // Spec 0023: si el torneo exige documento, el delegado (que entra al plantel) debe tenerlo.
        if (Boolean.TRUE.equals(torneo.getExigeDocumento())
                && directorioPersonas.requerir(delegadoPersonaId).numeroDocumento() == null) {
            throw documentoRequerido("Este torneo exige documento: agrega el tuyo en tu perfil antes de inscribir el equipo.");
        }
        if (equipoTorneoRepo.findByTorneoIdAndDelegadoPersonaId(torneoId, delegadoPersonaId).isPresent()) {
            throw ApiException.conflict("YA_INSCRITO", "Ya tienes una inscripcion para este torneo.");
        }
        if (jugadorEquipoRepo.existsByPersonaIdAndTorneoIdAndEstado(delegadoPersonaId, torneoId, EstadoMembresia.ACTIVO)) {
            throw ApiException.conflict("YA_EN_UN_EQUIPO_DEL_TORNEO",
                    "Ya juegas en un equipo de este torneo: no puedes inscribir otro.");
        }

        var equipo = equipoParaInscribir(delegadoPersonaId, req);
        if (equipoTorneoRepo.findByTorneoIdAndEquipoId(torneoId, equipo.getId()).isPresent()) {
            throw ApiException.conflict("YA_INSCRITO", "El equipo ya esta inscrito en este torneo.");
        }
        return torneoAdminService.toInscripcionDTO(
                registrarInscripcion(torneo, equipo, delegadoPersonaId, EstadoInscripcion.PENDIENTE_PAGO));
    }

    /**
     * Crea la inscripción y suma al delegado a su plantel. La usan la autoinscripción y el cupo
     * por invitación (spec 0026, camino b).
     */
    @Transactional
    public EquipoTorneo registrarInscripcion(terminus.co.edu.ufps.codecup.competicion.model.Torneo torneo, Equipo equipo,
                                             UUID delegadoPersonaId, EstadoInscripcion estado) {
        UUID torneoId = torneo.getId();
        var et = EquipoTorneo.builder()
                .torneo(torneo)
                .equipo(equipo)
                .delegadoPersonaId(delegadoPersonaId)
                .estadoInscripcion(estado)
                .build();
        equipoTorneoRepo.save(et);

        // Auto-inscribir al delegado como jugador, con snapshot de su persona.
        // El perfil deportivo es opcional (spec 0020): lo que falte queda en null.
        if (!jugadorEquipoRepo.existsByPersonaIdAndTorneoId(delegadoPersonaId, torneoId)) {
            var persona = directorioPersonas.buscar(delegadoPersonaId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "El delegado no tiene una persona registrada en CodeCup."));
            var perfil = perfilRepo.findById(delegadoPersonaId).orElse(null);
            var je = JugadorEquipo.builder()
                    .personaId(delegadoPersonaId)
                    .torneo(torneo)
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .alturaCmSnapshot(perfil != null ? perfil.getAlturaCm() : null)
                    .piernaHabilSnapshot(perfil != null ? perfil.getPiernaHabil() : null)
                    .posicionSnapshot(perfil != null ? perfil.getPosicion() : null)
                    .build();
            poblarSnapshots(je, persona.nombreCompleto());
            jugadorEquipoRepo.save(je);
        }

        return et;
    }

    @Transactional(readOnly = true)
    public List<InscripcionDTO> misInscripciones(UUID delegadoPersonaId) {
        return equipoTorneoRepo.findByDelegadoPersonaIdOrderByFechaInscripcionDesc(delegadoPersonaId)
                .stream()
                .map(torneoAdminService::toInscripcionDTO)
                .toList();
    }

    // Eliminado: el pago mock fue reemplazado por el flujo real de comprobantes (MS3).
    // El delegado sube un comprobante y el admin lo aprueba. Ya no hay auto-aprobacion.

    // ─────────────────────────────────────────────────────────
    //  HU44 — Delegado gestiona plantel directamente
    // ─────────────────────────────────────────────────────────

    @Transactional
    public MiembroEquipoDTO agregarMiembro(UUID delegadoPersonaId, UUID equipoTorneoId,
                                            AgregarMiembroRequest req) {
        var et = inscripcionDelDelegado(delegadoPersonaId, equipoTorneoId);

        // Spec 0018: el documento se normaliza igual que en el registro, para que
        // "1.090 123" y "1090123" sean la misma persona y no dos. Spec 0023: es opcional,
        // salvo que el torneo lo exija; sin documento, la persona siempre es nueva.
        String documento = normalizarDocumento(req.getNumeroDocumento());
        if (documento == null && Boolean.TRUE.equals(et.getTorneo().getExigeDocumento())) {
            throw ApiException.badRequest("DOCUMENTO_REQUERIDO",
                    "Este torneo exige documento: escribe el número de documento del jugador.");
        }
        var persona = documento == null ? null : directorioPersonas.buscarPorDocumento(documento).orElse(null);
        if (persona != null && persona.cuentaBloqueada()) {
            throw jugadorBloqueado();
        }

        // El nombre: el de la persona registrada (lo decide ella); si no tiene cuenta,
        // el que escribe el delegado; si no, el que ya tenía de otro torneo.
        String nombreEnviado = limpiarNombre(req.getNombre());
        String nombre;
        if (persona != null && persona.tieneCuenta()) {
            nombre = persona.nombreCompleto();
        } else if (nombreEnviado != null) {
            nombre = nombreEnviado;
        } else if (persona != null) {
            nombre = persona.nombreCompleto();
        } else {
            throw ApiException.badRequest("NOMBRE_REQUERIDO",
                    "Esta persona aun no tiene cuenta en CodeCup: escribe su nombre completo.");
        }

        if (persona != null && jugadorEquipoRepo.existsByPersonaIdAndTorneoIdAndEstado(
                persona.personaId(), et.getTorneo().getId(), EstadoMembresia.ACTIVO)) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        if (req.getNumeroCamiseta() != null) {
            if (jugadorEquipoRepo.existsByEquipoTorneoIdAndNumeroCamisetaAndEstado(
                    equipoTorneoId, req.getNumeroCamiseta(), EstadoMembresia.ACTIVO)) {
                throw new RuntimeException(
                        "El número " + req.getNumeroCamiseta() + " ya está asignado en este equipo.");
            }
        }

        // Spec 0022: quien no existía pasa a ser una persona sin cuenta. Si ya existía,
        // no se renombra: el nombre de este torneo vive en el snapshot (0018, criterio 4).
        if (persona == null) {
            persona = directorioPersonas.registrarSinCuenta(nombre, req.getTipoDocumento(), documento);
        }

        var existente = jugadorEquipoRepo.findByPersonaIdAndTorneoId(
                persona.personaId(), et.getTorneo().getId());

        JugadorEquipo je;
        if (existente.isPresent()) {
            je = existente.get();
            je.setEquipoTorneo(et);
            je.setEstado(EstadoMembresia.ACTIVO);
            je.setFechaInicio(LocalDate.now());
            je.setFechaFin(null);
            if (req.getNumeroCamiseta() != null) {
                je.setNumeroCamiseta(req.getNumeroCamiseta());
            }
        } else {
            je = JugadorEquipo.builder()
                    .personaId(persona.personaId())
                    .torneo(et.getTorneo())
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .numeroCamiseta(req.getNumeroCamiseta())
                    .build();
        }
        // Snapshot del nombre (se actualiza siempre que se agregue/reactive el miembro).
        poblarSnapshots(je, nombre);
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, persona, et);
    }

    /**
     * Mismo criterio que el registro (identidad). Sin documento (null o vacío) = null: es opcional
     * (spec 0023). Escrito pero sin letras ni números, o más largo que la columna = 400 VALIDACION.
     */
    private static String normalizarDocumento(String documento) {
        if (documento == null || documento.isBlank()) return null;
        String numero = Documentos.normalizar(documento);
        if (numero == null) {
            throw ApiException.badRequest("VALIDACION", "El documento no es válido.");
        }
        if (numero.length() > 20) {
            throw ApiException.badRequest("VALIDACION", "El documento no puede tener más de 20 caracteres.");
        }
        return numero;
    }

    /** Spec 0026: antes salía como 400 REGLA_NEGOCIO. */
    public static ApiException noDelegado() {
        return ApiException.forbidden("NO_DELEGADO_DE_LA_INSCRIPCION", "No eres el delegado de esta inscripción.");
    }

    private static ApiException documentoRequerido(String mensaje) {
        return ApiException.conflict("DOCUMENTO_REQUERIDO", mensaje);
    }

    /** Sin espacios al inicio ni al final; vacio = no enviado. */
    private static String limpiarNombre(String nombre) {
        if (nombre == null) return null;
        String limpio = nombre.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Una cuenta bloqueada (p. ej. por conducta antideportiva) no entra a ningun plantel. */
    private static ApiException jugadorBloqueado() {
        return ApiException.conflict("JUGADOR_BLOQUEADO",
                "Esta persona tiene la cuenta bloqueada: no puede entrar a un plantel.");
    }

    @Transactional
    public MiembroEquipoDTO removerMiembro(UUID delegadoPersonaId, UUID equipoTorneoId, UUID personaId) {
        var et = inscripcionDelDelegado(delegadoPersonaId, equipoTorneoId);

        var je = jugadorEquipoRepo.findByPersonaIdAndEquipoTorneoId(personaId, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (je.getPersonaId().equals(et.getDelegadoPersonaId())) {
            throw new RuntimeException("No puedes eliminar al delegado del plantel.");
        }

        if (je.getEstado() != EstadoMembresia.RETIRADO) {
            je.setEstado(EstadoMembresia.RETIRADO);
            je.setFechaFin(LocalDate.now());
            jugadorEquipoRepo.save(je);
        }

        return toMiembroDTO(je, directorioPersonas.buscar(personaId).orElse(null), et);
    }

    @Transactional
    public MiembroEquipoDTO actualizarCamiseta(UUID delegadoPersonaId, UUID equipoTorneoId,
                                                UUID personaId, Integer numeroCamiseta) {
        var et = inscripcionDelDelegado(delegadoPersonaId, equipoTorneoId);

        var je = jugadorEquipoRepo.findByPersonaIdAndEquipoTorneoId(personaId, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (numeroCamiseta != null) {
            var ocupado = jugadorEquipoRepo
                    .findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                    .stream()
                    .filter(j -> j.getEstado() == EstadoMembresia.ACTIVO
                            && j.getNumeroCamiseta() != null
                            && j.getNumeroCamiseta().equals(numeroCamiseta)
                            && !j.getPersonaId().equals(personaId))
                    .findAny();
            if (ocupado.isPresent()) {
                throw new RuntimeException(
                        "El número " + numeroCamiseta + " ya está asignado a otro jugador activo.");
            }
        }

        je.setNumeroCamiseta(numeroCamiseta);
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, directorioPersonas.buscar(personaId).orElse(null), et);
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembros(UUID delegadoPersonaId, UUID equipoTorneoId) {
        var et = inscripcionDelDelegado(delegadoPersonaId, equipoTorneoId);
        return miembrosDe(et);
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembrosAdmin(UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        return miembrosDe(et);
    }

    private List<MiembroEquipoDTO> miembrosDe(EquipoTorneo et) {
        var plantel = jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(et.getId());
        Map<UUID, PersonaResumen> personas = directorioPersonas.buscarVarias(
                plantel.stream().map(JugadorEquipo::getPersonaId).toList());
        return plantel.stream()
                .map(je -> toMiembroDTO(je, personas.get(je.getPersonaId()), et))
                .toList();
    }

    private EquipoTorneo inscripcionDelDelegado(UUID delegadoPersonaId, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoPersonaId().equals(delegadoPersonaId)) {
            throw noDelegado();
        }
        return et;
    }

    /**
     * Snapshot del nombre en este torneo: el registro historico, y el nombre que usa
     * quien no tiene cuenta. Sin padron ya no hay datos academicos (semestre, rol
     * academico): esos snapshots quedan vacios. Los llenara el CSV de informes (M5).
     */
    private void poblarSnapshots(JugadorEquipo je, String nombre) {
        je.setNombreSnapshot(nombre);
        je.setSnapshotAt(LocalDateTime.now());
    }

    /**
     * Construye el DTO del miembro. Prioridad para el nombre (spec 0018):
     *   1. apodo (cosmetico, opt-in del jugador)
     *   2. la persona registrada (con cuenta): el nombre lo decide ella
     *   3. snapshot del torneo (lo que escribio el delegado si no tiene cuenta)
     *   4. el nombre de la persona sin cuenta (filas viejas sin snapshot)
     */
    private MiembroEquipoDTO toMiembroDTO(JugadorEquipo je, PersonaResumen persona, EquipoTorneo et) {
        var perfil = perfilRepo.findById(je.getPersonaId()).orElse(null);
        String nombre;
        if (perfil != null && perfil.apodoVisible() != null) {
            nombre = perfil.apodoVisible();
        } else if (persona != null && persona.tieneCuenta()) {
            nombre = persona.nombreCompleto();
        } else if (je.getNombreSnapshot() != null && !je.getNombreSnapshot().isBlank()) {
            nombre = je.getNombreSnapshot();
        } else {
            nombre = persona != null ? persona.nombreCompleto() : null;
        }

        return MiembroEquipoDTO.builder()
                .personaId(je.getPersonaId())
                .numeroDocumento(persona != null ? persona.numeroDocumento() : null)
                .nombre(nombre)
                .posicion(perfil != null ? perfil.getPosicion() : null)
                .piernaHabil(perfil != null ? perfil.getPiernaHabil() : null)
                .alturaCm(perfil != null ? perfil.getAlturaCm() : null)
                .numeroCamiseta(je.getNumeroCamiseta())
                .estado(je.getEstado() != null ? je.getEstado().name() : null)
                .desde(je.getFechaInicio())
                .esDelegado(je.getPersonaId().equals(et.getDelegadoPersonaId()))
                .torneoEstado(et.getTorneo().getEstado().name())
                .tieneCuenta(persona != null && persona.tieneCuenta())
                .build();
    }
}

package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import terminus.co.edu.ufps.codecup.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionInfoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.AgregarMiembroRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.codecup.competicion.model.*;
import terminus.co.edu.ufps.codecup.competicion.repository.*;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegadoService {

    private final EquipoRepository equipoRepo;
    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final JugadorRepository jugadorRepo;
    private final TorneoAdminService torneoAdminService;
    private final DirectorioPersonas directorioPersonas;
    private final ComprobanteRepository comprobanteRepo;

    @Transactional(readOnly = true)
    public InscripcionInfoDTO obtenerInscripcionInfo(String delegadoCedula, UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        var et = equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(torneoId, delegadoCedula)
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
    public EquipoDTO crearEquipo(String delegadoCedula, CrearEquipoRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del equipo es obligatorio.");
        }
        // Garantiza la fila local de jugador (con nombre de la persona registrada) ANTES
        // de crear el equipo: la tabla equipos tiene FK a jugadores.
        garantizarJugadorLocal(delegadoCedula);

        // Un delegado solo puede tener un equipo base.
        var existente = equipoRepo.findByDelegadoCedula(delegadoCedula);
        if (existente.isPresent()) {
            throw new RuntimeException("Ya tienes un equipo creado: \""
                    + existente.get().getNombre() + "\". No puedes crear otro.");
        }

        var equipo = Equipo.builder()
                .nombre(req.getNombre().trim())
                .delegadoCedula(delegadoCedula)
                .build();
        equipoRepo.save(equipo);
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    /**
     * Garantiza una fila local en competicion.jugadores para la cedula dada.
     * Si no existe, la crea copiando nombre y correo de la persona registrada
     * (identidad.personas). Si nadie se registro con esa cedula, lanza error:
     * nunca queremos jugadores locales con nombre vacio (la tabla lo exige
     * NOT NULL y el frontend muestra el nombre en estadisticas/eventos).
     */
    private Jugador garantizarJugadorLocal(String cedula) {
        return jugadorRepo.findById(cedula).orElseGet(() -> {
            var persona = directorioPersonas.buscarPorDocumento(cedula)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "La cedula " + cedula + " no corresponde a ninguna persona registrada en CodeCup."));
            return jugadorRepo.save(Jugador.builder()
                    .cedula(cedula)
                    .nombre(persona.nombreCompleto())
                    .correo(persona.correo())
                    .build());
        });
    }

    @Transactional(readOnly = true)
    public EquipoDTO miEquipo(String delegadoCedula) {
        var equipo = equipoRepo.findByDelegadoCedula(delegadoCedula)
                .orElseThrow(() -> new ResourceNotFoundException("Aun no has creado tu equipo."));
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    @Transactional(readOnly = true)
    public List<TorneoDisponibleDTO> torneosDisponibles(String delegadoCedula) {
        var publicados = torneoRepo.findByEstadoInOrderByEdicionDesc(
                List.of(EstadoTorneo.PUBLICADO, EstadoTorneo.EN_CURSO));
        return publicados.stream().map(t -> {
            var inscripcion = equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(t.getId(), delegadoCedula);
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

    @Transactional
    public InscripcionDTO inscribir(String delegadoCedula, UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO) {
            throw new RuntimeException("Solo se puede inscribir a torneos PUBLICADOS.");
        }

        var equipoDto = miEquipo(delegadoCedula);
        var equipo = equipoRepo.findById(equipoDto.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado."));

        if (equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(torneoId, delegadoCedula).isPresent()) {
            throw new RuntimeException("Ya tienes una inscripcion para este torneo.");
        }
        if (equipoTorneoRepo.findByTorneoIdAndEquipoId(torneoId, equipo.getId()).isPresent()) {
            throw new RuntimeException("El equipo ya esta inscrito en este torneo.");
        }

        var et = EquipoTorneo.builder()
                .torneo(torneo)
                .equipo(equipo)
                .delegadoCedula(delegadoCedula)
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build();
        equipoTorneoRepo.save(et);

        // Auto-inscribir al delegado como jugador con snapshot de su persona registrada.
        if (!jugadorEquipoRepo.existsByCedulaAndTorneoId(delegadoCedula, torneoId)) {
            var persona = directorioPersonas.buscarPorDocumento(delegadoCedula)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "El delegado no tiene una persona registrada en CodeCup."));
            // jugador_equipo.cedula tiene FK a jugadores, asi que garantizamos
            // la fila local antes de insertar JugadorEquipo.
            var jugadorLocal = garantizarJugadorLocal(delegadoCedula);
            if (jugadorLocal.getAlturaCm() == null
                    || jugadorLocal.getPiernaHabil() == null || jugadorLocal.getPiernaHabil().isBlank()
                    || jugadorLocal.getPosicion() == null || jugadorLocal.getPosicion().isBlank()) {
                throw new RuntimeException(
                        "Completa tu perfil deportivo (altura, pierna habil, posicion) antes de inscribir el equipo.");
            }
            var je = JugadorEquipo.builder()
                    .cedula(delegadoCedula)
                    .torneo(torneo)
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .alturaCmSnapshot(jugadorLocal.getAlturaCm())
                    .piernaHabilSnapshot(jugadorLocal.getPiernaHabil())
                    .posicionSnapshot(jugadorLocal.getPosicion())
                    .build();
            poblarSnapshots(je, persona);
            jugadorEquipoRepo.save(je);
        }

        return torneoAdminService.toInscripcionDTO(et);
    }

    @Transactional(readOnly = true)
    public List<InscripcionDTO> misInscripciones(String delegadoCedula) {
        return equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(delegadoCedula)
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
    public MiembroEquipoDTO agregarMiembro(String delegadoCedula, UUID equipoTorneoId,
                                            AgregarMiembroRequest req) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        // La cedula debe ser de una persona registrada en CodeCup. La spec 0018
        // permitira agregar miembros sin cuenta (el delegado enviara el nombre).
        var persona = directorioPersonas.buscarPorDocumento(req.getCedula())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La cedula " + req.getCedula() + " no esta registrada en CodeCup. "
                                + "Pidele a la persona que se registre."));

        // Asegurar que exista Jugador local. Tomamos nombre/correo de la persona
        // porque la tabla los exige NOT NULL y porque varias FKs apuntan aqui.
        var jugador = jugadorRepo.findById(req.getCedula())
                .orElseGet(() -> jugadorRepo.save(Jugador.builder()
                        .cedula(req.getCedula())
                        .nombre(persona.nombreCompleto())
                        .correo(persona.correo())
                        .build()));

        if (jugadorEquipoRepo.existsByCedulaAndTorneoIdAndEstado(
                req.getCedula(), et.getTorneo().getId(), EstadoMembresia.ACTIVO)) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        if (req.getNumeroCamiseta() != null) {
            if (jugadorEquipoRepo.existsByEquipoTorneoIdAndNumeroCamisetaAndEstado(
                    equipoTorneoId, req.getNumeroCamiseta(), EstadoMembresia.ACTIVO)) {
                throw new RuntimeException(
                        "El número " + req.getNumeroCamiseta() + " ya está asignado en este equipo.");
            }
        }

        var existente = jugadorEquipoRepo.findByCedulaAndTorneoId(
                req.getCedula(), et.getTorneo().getId());

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
                    .cedula(jugador.getCedula())
                    .torneo(et.getTorneo())
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .numeroCamiseta(req.getNumeroCamiseta())
                    .build();
        }
        // Snapshot del nombre (se actualiza siempre que se agregue/reactive el miembro).
        poblarSnapshots(je, persona);
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugador, et, persona);
    }

    @Transactional
    public MiembroEquipoDTO removerMiembro(String delegadoCedula, UUID equipoTorneoId, String cedula) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var je = jugadorEquipoRepo.findByCedulaAndEquipoTorneoId(cedula, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (je.getCedula().equals(et.getDelegadoCedula())) {
            throw new RuntimeException("No puedes eliminar al delegado del plantel.");
        }

        if (je.getEstado() == EstadoMembresia.RETIRADO) {
            return toMiembroDTO(je, jugadorRepo.findById(cedula).orElse(null), et, null);
        }

        je.setEstado(EstadoMembresia.RETIRADO);
        je.setFechaFin(LocalDate.now());
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugadorRepo.findById(cedula).orElse(null), et, null);
    }

    @Transactional
    public MiembroEquipoDTO actualizarCamiseta(String delegadoCedula, UUID equipoTorneoId,
                                                String cedula, Integer numeroCamiseta) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var je = jugadorEquipoRepo.findByCedulaAndEquipoTorneoId(cedula, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (numeroCamiseta != null) {
            var ocupado = jugadorEquipoRepo
                    .findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                    .stream()
                    .filter(j -> j.getEstado() == EstadoMembresia.ACTIVO
                            && j.getNumeroCamiseta() != null
                            && j.getNumeroCamiseta().equals(numeroCamiseta)
                            && !j.getCedula().equals(cedula))
                    .findAny();
            if (ocupado.isPresent()) {
                throw new RuntimeException(
                        "El número " + numeroCamiseta + " ya está asignado a otro jugador activo.");
            }
        }

        je.setNumeroCamiseta(numeroCamiseta);
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugadorRepo.findById(cedula).orElse(null), et, null);
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembros(String delegadoCedula, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }
        return jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                .stream()
                .map(je -> toMiembroDTO(je, jugadorRepo.findById(je.getCedula()).orElse(null), et, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembrosAdmin(UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        return jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                .stream()
                .map(je -> toMiembroDTO(je, jugadorRepo.findById(je.getCedula()).orElse(null), et, null))
                .toList();
    }

    /**
     * Sin padron ya no hay datos academicos (semestre, rol academico): esos
     * snapshots quedan vacios. Los llenara el CSV de informes (M5).
     */
    private void poblarSnapshots(JugadorEquipo je, PersonaResumen persona) {
        je.setNombreSnapshot(persona.nombreCompleto());
        je.setSnapshotAt(LocalDateTime.now());
    }

    /**
     * Construye el DTO del miembro. Prioridad para el nombre:
     *   1. apodo (cosmetico, opt-in del jugador)
     *   2. snapshot guardado al inscribirse (estable)
     *   3. la persona registrada (si los dos anteriores estan vacios)
     */
    private MiembroEquipoDTO toMiembroDTO(JugadorEquipo je, Jugador jugador, EquipoTorneo et,
                                          PersonaResumen personaYaConocida) {
        String nombre = null;
        if (jugador != null && jugador.getApodo() != null && !jugador.getApodo().isBlank()) {
            nombre = jugador.getApodo();
        } else if (je.getNombreSnapshot() != null && !je.getNombreSnapshot().isBlank()) {
            nombre = je.getNombreSnapshot();
        } else {
            var persona = personaYaConocida != null
                    ? personaYaConocida
                    : directorioPersonas.buscarPorDocumento(je.getCedula()).orElse(null);
            nombre = persona != null ? persona.nombreCompleto() : null;
        }

        return MiembroEquipoDTO.builder()
                .cedula(je.getCedula())
                .nombre(nombre)
                .posicion(jugador != null ? jugador.getPosicion() : null)
                .piernaHabil(jugador != null ? jugador.getPiernaHabil() : null)
                .alturaCm(jugador != null ? jugador.getAlturaCm() : null)
                .numeroCamiseta(je.getNumeroCamiseta())
                .estado(je.getEstado() != null ? je.getEstado().name() : null)
                .desde(je.getFechaInicio())
                .esDelegado(je.getCedula().equals(et.getDelegadoCedula()))
                .torneoEstado(et.getTorneo().getEstado().name())
                .build();
    }
}

package terminus.co.edu.ufps.codecup.finanzas.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EventoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.MultaConfigRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.finanzas.dto.GenerarMultaRequest;
import terminus.co.edu.ufps.codecup.finanzas.dto.MultaDTO;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.codecup.finanzas.model.Multa;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoSancion;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;
import terminus.co.edu.ufps.codecup.notificaciones.NotificacionPublisher;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultaService {

    private final MultaRepository multaRepo;
    private final PartidoRepository partidoRepo;
    private final EventoPartidoRepository eventoRepo;
    private final MultaConfigRepository multaConfigRepo;
    private final NotificacionPublisher notificacionPublisher;
    private final DirectorioPersonas directorioPersonas;

    @Transactional
    public void generarPorPartido(UUID partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));

        if (partido.getEstado() == EstadoPartido.WO
                || "SIN_PAGO_ARBITRAJE".equals(partido.getTipoCierre())) {
            return;
        }

        var config = multaConfigRepo.findByTorneoId(partido.getTorneo().getId());
        if (config.isEmpty()) {
            log.warn("No hay multa_config para torneo {}, no se generan multas.", partido.getTorneo().getId());
            return;
        }
        var cfg = config.get();

        var eventos = eventoRepo.findByPartidoIdOrderByOrdenAsc(partidoId);
        for (var ev : eventos) {
            if (ev.getTipoEvento() == TipoEvento.GOL) continue;
            if (ev.getPersonaId() == null) continue;

            TipoSancion tipoSancion = mapTipoSancion(ev.getTipoEvento());
            BigDecimal monto = calcularMonto(tipoSancion, cfg);
            int suspension = (tipoSancion == TipoSancion.ROJA || tipoSancion == TipoSancion.ROJA_DIRECTA)
                    ? cfg.getFechasSuspensionRoja() : 0;

            var multa = Multa.builder()
                    .personaId(ev.getPersonaId())
                    .partidoId(partidoId)
                    .equipoTorneoId(ev.getEquipoTorneo().getId())
                    .torneoId(partido.getTorneo().getId())
                    .tipoSancion(tipoSancion)
                    .monto(monto)
                    .partidosSuspension(suspension)
                    .partidosSuspensionRestantes(suspension)
                    .build();
            multaRepo.save(multa);

            if (tipoSancion == TipoSancion.ROJA || tipoSancion == TipoSancion.ROJA_DIRECTA) {
                notificacionPublisher.notificarRojaSuspendida(
                        ev.getPersonaId(), partidoId, suspension);
            }

            log.info("[FINANZAS] Multa {} generada para persona={} tipo={} monto={}",
                    multa.getId(), multa.getPersonaId(), tipoSancion, monto);
        }
    }

    @Transactional
    public MultaDTO generar(GenerarMultaRequest req) {
        TipoSancion tipo;
        try {
            tipo = TipoSancion.valueOf(req.getTipoSancion());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Tipo de sancion invalido: " + req.getTipoSancion());
        }

        var config = multaConfigRepo.findByTorneoId(req.getTorneoId());
        BigDecimal monto = config.map(c -> calcularMonto(tipo, c)).orElse(BigDecimal.ZERO);
        int suspension = (tipo == TipoSancion.ROJA || tipo == TipoSancion.ROJA_DIRECTA)
                ? config.map(c -> c.getFechasSuspensionRoja()).orElse(1) : 0;

        var multa = Multa.builder()
                .personaId(req.getPersonaId())
                .partidoId(req.getPartidoId())
                .equipoTorneoId(req.getEquipoTorneoId())
                .torneoId(req.getTorneoId())
                .tipoSancion(tipo)
                .monto(monto)
                .partidosSuspension(suspension)
                .partidosSuspensionRestantes(suspension)
                .build();
        multaRepo.save(multa);

        if (tipo == TipoSancion.ROJA || tipo == TipoSancion.ROJA_DIRECTA) {
            notificacionPublisher.notificarRojaSuspendida(
                    req.getPersonaId(), req.getPartidoId(), suspension);
        }

        log.info("[FINANZAS] Multa {} generada para persona={} tipo={} monto={}",
                multa.getId(), multa.getPersonaId(), tipo, monto);
        return toDTO(multa);
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> misMultas(UUID personaId) {
        return multaRepo.findByPersonaId(personaId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> multasActivas(UUID torneoId) {
        return multaRepo.findByTorneoIdAndEstado(torneoId, EstadoMulta.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> multasPorTorneo(UUID torneoId) {
        return multaRepo.findByTorneoId(torneoId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> multasPendientesJugador(UUID personaId, UUID torneoId) {
        return multaRepo.findByPersonaIdAndTorneoIdAndEstado(personaId, torneoId, EstadoMulta.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public boolean jugadorTieneDeudas(UUID personaId) {
        return multaRepo.countByPersonaIdAndEstado(personaId, EstadoMulta.PENDIENTE) > 0
                || multaRepo.countByPersonaIdAndEstado(personaId, EstadoMulta.EN_REVISION) > 0;
    }

    @Transactional(readOnly = true)
    public ElegibilidadDTO consultarElegibilidad(UUID personaId, UUID torneoId) {
        List<String> motivos = new ArrayList<>();

        boolean suspensionActiva = multaRepo.existsByPersonaIdAndTorneoIdAndTipoSancionAndPartidosSuspensionRestantesGreaterThanAndHabilitadoManualFalse(
                personaId, torneoId);
        if (suspensionActiva) {
            motivos.add("SUSPENSION_ACTIVA");
        }

        boolean multaPendiente = multaRepo.existsByPersonaIdAndTorneoIdAndEstado(personaId, torneoId, EstadoMulta.PENDIENTE);
        if (multaPendiente) {
            motivos.add("MULTA_PENDIENTE");
        }

        return new ElegibilidadDTO(motivos.isEmpty(), motivos);
    }

    @Transactional
    public MultaDTO registrarPago(UUID multaId, UUID partidoPagoId, UUID cuentaActor) {
        var multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));

        if (multa.getEstado() == EstadoMulta.PAGADA) {
            var pagador = java.util.Objects.requireNonNullElse(directorioPersonas.nombreDeCuenta(multa.getPagadoPorCuentaId()), "desconocido");
            throw new IllegalStateException(
                    "Esta multa ya fue pagada por " + pagador + " el dia " + multa.getFechaPago());
        }

        multa.setEstado(EstadoMulta.PAGADA);
        multa.setPagadoPorCuentaId(cuentaActor);
        multa.setPartidoPagoId(partidoPagoId);
        multa.setFechaPago(LocalDateTime.now());
        multaRepo.save(multa);

        log.info("[FINANZAS] Multa {} PAGADA por {} en partido {}",
                multaId, cuentaActor, partidoPagoId);
        return toDTO(multa);
    }

    @Transactional
    public RegistrarPagoTodasResult registrarPagoTodas(UUID personaId, UUID torneoId, UUID partidoPagoId, UUID cuentaActor) {
        var pendientes = multaRepo.findByPersonaIdAndTorneoIdAndEstado(personaId, torneoId, EstadoMulta.PENDIENTE);
        if (pendientes.isEmpty()) {
            throw new RuntimeException("El jugador no tiene multas pendientes en este torneo.");
        }

        BigDecimal totalPagado = BigDecimal.ZERO;
        List<UUID> pagadas = new ArrayList<>();

        for (var multa : pendientes) {
            multa.setEstado(EstadoMulta.PAGADA);
            multa.setPagadoPorCuentaId(cuentaActor);
            multa.setPartidoPagoId(partidoPagoId);
            multa.setFechaPago(LocalDateTime.now());
            multaRepo.save(multa);
            totalPagado = totalPagado.add(multa.getMonto());
            pagadas.add(multa.getId());
        }

        log.info("[FINANZAS] Pago total de {} para persona={} en torneo={}: {} multas, total={}",
                cuentaActor, personaId, torneoId, pagadas.size(), totalPagado);

        return new RegistrarPagoTodasResult(totalPagado, pagadas);
    }

    @Transactional
    public MultaDTO habilitarExcepcion(UUID multaId, String motivo, UUID cuentaAdmin) {
        var multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));

        if (!Boolean.TRUE.equals(multa.getHabilitadoManual()) &&
                (multa.getPartidosSuspensionRestantes() == null || multa.getPartidosSuspensionRestantes() <= 0)) {
            throw new RuntimeException("La multa no tiene suspension activa. No se requiere habilitacion.");
        }

        multa.setHabilitadoManual(true);
        multa.setHabilitadoPorCuentaId(cuentaAdmin);
        multa.setHabilitadoEn(LocalDateTime.now());
        multa.setMotivoHabilitacion(motivo);
        multaRepo.save(multa);

        log.info("[FINANZAS] Excepcion habilitada para multa {} por {}: {}",
                multaId, cuentaAdmin, motivo);
        return toDTO(multa);
    }

    @Transactional
    public void decrementarSuspensiones(UUID equipoTorneoId) {
        var activas = multaRepo.findSuspensionesActivasByEquipoTorneo(equipoTorneoId);
        for (var multa : activas) {
            int restantes = multa.getPartidosSuspensionRestantes() != null
                    ? multa.getPartidosSuspensionRestantes() : 0;
            if (restantes > 0) {
                multa.setPartidosSuspensionRestantes(restantes - 1);
                multaRepo.save(multa);
                log.info("[FINANZAS] Suspension decrementada para multa {}: ahora restan {}",
                        multa.getId(), restantes - 1);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> suspensionesActivas(UUID personaId, UUID torneoId) {
        return multaRepo.findSuspensionesActivas(personaId, torneoId).stream()
                .map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<JugadorBloqueadoDTO> jugadoresBloqueados(UUID partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        UUID torneoId = partido.getTorneo().getId();

        List<JugadorBloqueadoDTO> bloqueados = new ArrayList<>();

        for (UUID equipoTorneoId : List.of(
                partido.getEquipoLocalTorneo().getId(),
                partido.getEquipoVisitanteTorneo().getId())) {
            var jugadores = multaRepo.findJugadoresBloqueados(equipoTorneoId, torneoId);
            for (Object[] row : jugadores) {
                bloqueados.add(new JugadorBloqueadoDTO(
                        (UUID) row[0],
                        (UUID) row[1],
                        (Boolean) row[2],
                        (Boolean) row[3]));
            }
        }

        return bloqueados;
    }

    private BigDecimal calcularMonto(TipoSancion tipo, terminus.co.edu.ufps.codecup.competicion.model.MultaConfig cfg) {
        return switch (tipo) {
            case AMARILLA -> cfg.getMontoAmarilla();
            case AZUL -> cfg.getMontoAzul();
            case ROJA, ROJA_DIRECTA -> cfg.getMontoRoja();
            case ACUMULACION_AMARILLAS -> cfg.getMontoAmarilla();
        };
    }

    private TipoSancion mapTipoSancion(TipoEvento tipoEvento) {
        return switch (tipoEvento) {
            case AMARILLA -> TipoSancion.AMARILLA;
            case AZUL -> TipoSancion.AZUL;
            case ROJA -> TipoSancion.ROJA;
            default -> throw new IllegalArgumentException("Tipo de evento no mapeable a sancion: " + tipoEvento);
        };
    }

    private MultaDTO toDTO(Multa m) {
        return MultaDTO.builder()
                .id(m.getId())
                .personaId(m.getPersonaId())
                .jugadorNombre(directorioPersonas.buscar(m.getPersonaId()).map(PersonaResumen::nombreCompleto).orElse(null))
                .partidoId(m.getPartidoId())
                .equipoTorneoId(m.getEquipoTorneoId())
                .torneoId(m.getTorneoId())
                .tipoSancion(m.getTipoSancion().name())
                .monto(m.getMonto())
                .estado(m.getEstado().name())
                .partidosSuspension(m.getPartidosSuspension())
                .partidosSuspensionRestantes(m.getPartidosSuspensionRestantes())
                .pagadoPorCuentaId(m.getPagadoPorCuentaId())
                .pagadoPor(directorioPersonas.nombreDeCuenta(m.getPagadoPorCuentaId()))
                .partidoPagoId(m.getPartidoPagoId())
                .habilitadoManual(m.getHabilitadoManual())
                .habilitadoPor(directorioPersonas.nombreDeCuenta(m.getHabilitadoPorCuentaId()))
                .habilitadoEn(m.getHabilitadoEn())
                .motivoHabilitacion(m.getMotivoHabilitacion())
                .fechaGeneracion(m.getFechaGeneracion())
                .fechaPago(m.getFechaPago())
                .build();
    }

    public record ElegibilidadDTO(boolean apto, List<String> motivos) {
        public String formatMotivos() {
            return String.join(", ", motivos);
        }
    }

    public record RegistrarPagoTodasResult(BigDecimal totalPagado, List<UUID> multasPagadasIds) {}

    public record JugadorBloqueadoDTO(UUID personaId, UUID equipoTorneoId, boolean suspensionActiva, boolean multaPendiente) {}
}

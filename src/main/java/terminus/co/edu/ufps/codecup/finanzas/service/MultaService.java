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

@Slf4j
@Service
@RequiredArgsConstructor
public class MultaService {

    private final MultaRepository multaRepo;
    private final PartidoRepository partidoRepo;
    private final EventoPartidoRepository eventoRepo;
    private final MultaConfigRepository multaConfigRepo;
    private final NotificacionPublisher notificacionPublisher;

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
            if (ev.getCedula() == null || ev.getCedula().isBlank()) continue;

            TipoSancion tipoSancion = mapTipoSancion(ev.getTipoEvento());
            BigDecimal monto = calcularMonto(tipoSancion, cfg);
            int suspension = (tipoSancion == TipoSancion.ROJA || tipoSancion == TipoSancion.ROJA_DIRECTA)
                    ? cfg.getFechasSuspensionRoja() : 0;

            var multa = Multa.builder()
                    .cedula(ev.getCedula())
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
                        ev.getCedula(), partidoId, suspension);
            }

            log.info("[FINANZAS] Multa {} generada para cedula={} tipo={} monto={}",
                    multa.getId(), multa.getCedula(), tipoSancion, monto);
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
                .cedula(req.getCedulaJugador())
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
                    req.getCedulaJugador(), req.getPartidoId(), suspension);
        }

        log.info("[FINANZAS] Multa {} generada para cedula={} tipo={} monto={}",
                multa.getId(), multa.getCedula(), tipo, monto);
        return toDTO(multa);
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> misMultas(String cedula) {
        return multaRepo.findByCedula(cedula).stream().map(this::toDTO).toList();
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
    public List<MultaDTO> multasPendientesJugador(String cedula, UUID torneoId) {
        return multaRepo.findByCedulaAndTorneoIdAndEstado(cedula, torneoId, EstadoMulta.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public boolean jugadorTieneDeudas(String cedula) {
        return multaRepo.countByCedulaAndEstado(cedula, EstadoMulta.PENDIENTE) > 0
                || multaRepo.countByCedulaAndEstado(cedula, EstadoMulta.EN_REVISION) > 0;
    }

    @Transactional(readOnly = true)
    public ElegibilidadDTO consultarElegibilidad(String cedula, UUID torneoId) {
        List<String> motivos = new ArrayList<>();

        boolean suspensionActiva = multaRepo.existsByCedulaAndTorneoIdAndTipoSancionAndPartidosSuspensionRestantesGreaterThanAndHabilitadoManualFalse(
                cedula, torneoId);
        if (suspensionActiva) {
            motivos.add("SUSPENSION_ACTIVA");
        }

        boolean multaPendiente = multaRepo.existsByCedulaAndTorneoIdAndEstado(cedula, torneoId, EstadoMulta.PENDIENTE);
        if (multaPendiente) {
            motivos.add("MULTA_PENDIENTE");
        }

        return new ElegibilidadDTO(motivos.isEmpty(), motivos);
    }

    @Transactional
    public MultaDTO registrarPago(UUID multaId, UUID partidoPagoId, String cedulaArbitro) {
        var multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));

        if (multa.getEstado() == EstadoMulta.PAGADA) {
            var pagador = multa.getPagadoPorCedula() != null ? multa.getPagadoPorCedula() : "desconocido";
            throw new IllegalStateException(
                    "Esta multa ya fue pagada por " + pagador + " el dia " + multa.getFechaPago());
        }

        multa.setEstado(EstadoMulta.PAGADA);
        multa.setPagadoPorCedula(cedulaArbitro);
        multa.setPartidoPagoId(partidoPagoId);
        multa.setFechaPago(LocalDateTime.now());
        multaRepo.save(multa);

        log.info("[FINANZAS] Multa {} PAGADA por {} en partido {}",
                multaId, cedulaArbitro, partidoPagoId);
        return toDTO(multa);
    }

    @Transactional
    public RegistrarPagoTodasResult registrarPagoTodas(String cedula, UUID torneoId, UUID partidoPagoId, String cedulaArbitro) {
        var pendientes = multaRepo.findByCedulaAndTorneoIdAndEstado(cedula, torneoId, EstadoMulta.PENDIENTE);
        if (pendientes.isEmpty()) {
            throw new RuntimeException("El jugador no tiene multas pendientes en este torneo.");
        }

        BigDecimal totalPagado = BigDecimal.ZERO;
        List<UUID> pagadas = new ArrayList<>();

        for (var multa : pendientes) {
            multa.setEstado(EstadoMulta.PAGADA);
            multa.setPagadoPorCedula(cedulaArbitro);
            multa.setPartidoPagoId(partidoPagoId);
            multa.setFechaPago(LocalDateTime.now());
            multaRepo.save(multa);
            totalPagado = totalPagado.add(multa.getMonto());
            pagadas.add(multa.getId());
        }

        log.info("[FINANZAS] Pago total de {} para cedula={} en torneo={}: {} multas, total={}",
                cedulaArbitro, cedula, torneoId, pagadas.size(), totalPagado);

        return new RegistrarPagoTodasResult(totalPagado, pagadas);
    }

    @Transactional
    public MultaDTO habilitarExcepcion(UUID multaId, String motivo, String cedulaAdmin) {
        var multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));

        if (!Boolean.TRUE.equals(multa.getHabilitadoManual()) &&
                (multa.getPartidosSuspensionRestantes() == null || multa.getPartidosSuspensionRestantes() <= 0)) {
            throw new RuntimeException("La multa no tiene suspension activa. No se requiere habilitacion.");
        }

        multa.setHabilitadoManual(true);
        multa.setHabilitadoPorCedula(cedulaAdmin);
        multa.setHabilitadoEn(LocalDateTime.now());
        multa.setMotivoHabilitacion(motivo);
        multaRepo.save(multa);

        log.info("[FINANZAS] Excepcion habilitada para multa {} por {}: {}",
                multaId, cedulaAdmin, motivo);
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
    public List<MultaDTO> suspensionesActivas(String cedula, UUID torneoId) {
        return multaRepo.findSuspensionesActivas(cedula, torneoId).stream()
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
                        (String) row[0],
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
                .cedula(m.getCedula())
                .partidoId(m.getPartidoId())
                .equipoTorneoId(m.getEquipoTorneoId())
                .torneoId(m.getTorneoId())
                .tipoSancion(m.getTipoSancion().name())
                .monto(m.getMonto())
                .estado(m.getEstado().name())
                .partidosSuspension(m.getPartidosSuspension())
                .partidosSuspensionRestantes(m.getPartidosSuspensionRestantes())
                .pagadoPorCedula(m.getPagadoPorCedula())
                .partidoPagoId(m.getPartidoPagoId())
                .habilitadoManual(m.getHabilitadoManual())
                .habilitadoPorCedula(m.getHabilitadoPorCedula())
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

    public record JugadorBloqueadoDTO(String cedula, UUID equipoTorneoId, boolean suspensionActiva, boolean multaPendiente) {}
}

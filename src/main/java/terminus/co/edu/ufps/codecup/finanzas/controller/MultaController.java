package terminus.co.edu.ufps.codecup.finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.finanzas.dto.*;
import terminus.co.edu.ufps.codecup.finanzas.service.MultaService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finanzas/multas")
@RequiredArgsConstructor
public class MultaController {

    private final MultaService multaService;

    @GetMapping("/mias")
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<List<MultaDTO>> misMultas(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.misMultas(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/activas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<MultaDTO>> activas(@RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.multasActivas(torneoId));
    }

    @GetMapping("/torneo/{torneoId}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<MultaDTO>> porTorneo(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(multaService.multasPorTorneo(torneoId));
    }

    @GetMapping("/torneo/{torneoId}/bolsa")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> bolsa(@PathVariable UUID torneoId) {
        var multas = multaService.multasPorTorneo(torneoId);
        var pagadas = multas.stream().filter(m -> "PAGADA".equals(m.getEstado())).toList();
        var pendientes = multas.stream().filter(m -> "PENDIENTE".equals(m.getEstado())).toList();

        var totalPagado = pagadas.stream()
                .map(m -> m.getMonto() != null ? m.getMonto() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var totalPendiente = pendientes.stream()
                .map(m -> m.getMonto() != null ? m.getMonto() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        var desglosePartido = pagadas.stream()
                .filter(m -> m.getPartidoPagoId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        MultaDTO::getPartidoPagoId,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                list -> {
                                    var total = list.stream()
                                            .map(m -> m.getMonto() != null ? m.getMonto() : java.math.BigDecimal.ZERO)
                                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                                    return BolsaMultasDTO.DesglosePartido.builder()
                                            .partidoPagoId(list.get(0).getPartidoPagoId())
                                            .total(total)
                                            .cantidad((long) list.size())
                                            .build();
                                })))
                .values().stream().toList();

        var desgloseArbitro = pagadas.stream()
                .filter(m -> m.getPagadoPorCedula() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        MultaDTO::getPagadoPorCedula,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                list -> {
                                    var total = list.stream()
                                            .map(m -> m.getMonto() != null ? m.getMonto() : java.math.BigDecimal.ZERO)
                                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                                    return BolsaMultasDTO.DesgloseArbitro.builder()
                                            .pagadoPorCedula(list.get(0).getPagadoPorCedula())
                                            .total(total)
                                            .cantidad((long) list.size())
                                            .build();
                                })))
                .values().stream().toList();

        return ResponseEntity.ok(BolsaMultasDTO.builder()
                .totalPagado(totalPagado)
                .totalPendiente(totalPendiente)
                .desglosePorPartido(desglosePartido)
                .desglosePorArbitro(desgloseArbitro)
                .build());
    }

    @PostMapping("/generar")
    @PreAuthorize("hasAuthority('SCOPE_internal')")
    public ResponseEntity<MultaDTO> generar(@Valid @RequestBody GenerarMultaRequest req) {
        return ResponseEntity.ok(multaService.generar(req));
    }

    @GetMapping("/elegibilidad/{cedula}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DELEGADO','ARBITRO') or hasAuthority('SCOPE_internal')")
    public ResponseEntity<Boolean> elegible(@PathVariable String cedula) {
        return ResponseEntity.ok(!multaService.jugadorTieneDeudas(cedula));
    }

    @GetMapping("/elegibilidad/{cedula}/detalle")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<ElegibilidadResponse> elegibilidadDetalle(
            @PathVariable String cedula,
            @RequestParam UUID torneoId) {
        var el = multaService.consultarElegibilidad(cedula, torneoId);
        return ResponseEntity.ok(ElegibilidadResponse.builder()
                .apto(el.apto())
                .motivos(el.motivos())
                .build());
    }

    @GetMapping("/jugador/{cedula}/pendientes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<MultaDTO>> pendientesJugador(
            @PathVariable String cedula,
            @RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.multasPendientesJugador(cedula, torneoId));
    }

    @PostMapping("/{multaId}/registrar-pago")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<MultaDTO> registrarPago(
            @PathVariable UUID multaId,
            @RequestBody RegistrarPagoTodasRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.registrarPago(
                multaId, req.getPartidoPagoId(), jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/jugador/{cedula}/registrar-pago-todas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<RegistrarPagoResult> registrarPagoTodas(
            @PathVariable String cedula,
            @Valid @RequestBody RegistrarPagoTodasRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        var result = multaService.registrarPagoTodas(
                cedula, req.getTorneoId(), req.getPartidoPagoId(), jwt.getClaimAsString("cedula"));
        return ResponseEntity.ok(RegistrarPagoResult.builder()
                .totalPagado(result.totalPagado())
                .multasPagadasIds(result.multasPagadasIds())
                .build());
    }

    @GetMapping("/cedula/{cedula}/suspensiones")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<MultaDTO>> suspensiones(
            @PathVariable String cedula,
            @RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.suspensionesActivas(cedula, torneoId));
    }

    @GetMapping("/partido/{partidoId}/bloqueados")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<?>> bloqueados(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(multaService.jugadoresBloqueados(partidoId));
    }

    @PostMapping("/{multaId}/habilitar-excepcion")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<MultaDTO> habilitarExcepcion(
            @PathVariable UUID multaId,
            @Valid @RequestBody HabilitarExcepcionRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.habilitarExcepcion(
                multaId, req.getMotivo(), jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/{id}/habilitar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<MultaDTO> habilitarLegacy(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.habilitarExcepcion(
                id, "Habilitacion legacy", jwt.getClaimAsString("cedula")));
    }
}

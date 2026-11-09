package terminus.co.edu.ufps.codecup.finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.finanzas.dto.*;
import terminus.co.edu.ufps.codecup.finanzas.service.MultaService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

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
        return ResponseEntity.ok(multaService.misMultas(JwtClaims.personaId(jwt)));
    }

    @GetMapping("/activas")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<List<MultaDTO>> activas(@RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.multasActivas(torneoId));
    }

    @GetMapping("/torneo/{torneoId}")
    @PreAuthorize("@authz.staffDeTorneo(#torneoId)")
    public ResponseEntity<List<MultaDTO>> porTorneo(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(multaService.multasPorTorneo(torneoId));
    }

    @GetMapping("/torneo/{torneoId}/bolsa")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
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
                .filter(m -> m.getPagadoPorCuentaId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        MultaDTO::getPagadoPorCuentaId,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                list -> {
                                    var total = list.stream()
                                            .map(m -> m.getMonto() != null ? m.getMonto() : java.math.BigDecimal.ZERO)
                                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                                    return BolsaMultasDTO.DesgloseArbitro.builder()
                                            .pagadoPorCuentaId(list.get(0).getPagadoPorCuentaId())
                                            .pagadoPor(list.get(0).getPagadoPor())
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

    @GetMapping("/elegibilidad/{personaId}")
    @PreAuthorize("hasRole('DELEGADO') or hasAuthority('SCOPE_internal') or @authz.staffDeAlgunTorneo()")
    public ResponseEntity<Boolean> elegible(@PathVariable UUID personaId) {
        return ResponseEntity.ok(!multaService.jugadorTieneDeudas(personaId));
    }

    @GetMapping("/elegibilidad/{personaId}/detalle")
    @PreAuthorize("@authz.staffDeTorneo(#torneoId)")
    public ResponseEntity<ElegibilidadResponse> elegibilidadDetalle(
            @PathVariable UUID personaId,
            @RequestParam UUID torneoId) {
        var el = multaService.consultarElegibilidad(personaId, torneoId);
        return ResponseEntity.ok(ElegibilidadResponse.builder()
                .apto(el.apto())
                .motivos(el.motivos())
                .build());
    }

    @GetMapping("/jugador/{personaId}/pendientes")
    @PreAuthorize("@authz.staffDeTorneo(#torneoId)")
    public ResponseEntity<List<MultaDTO>> pendientesJugador(
            @PathVariable UUID personaId,
            @RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.multasPendientesJugador(personaId, torneoId));
    }

    @PostMapping("/{multaId}/registrar-pago")
    @PreAuthorize("@authz.staffDeMulta(#multaId)")
    public ResponseEntity<MultaDTO> registrarPago(
            @PathVariable UUID multaId,
            @RequestBody RegistrarPagoTodasRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.registrarPago(
                multaId, req.getPartidoPagoId(), JwtClaims.cuentaId(jwt)));
    }

    @PostMapping("/jugador/{personaId}/registrar-pago-todas")
    @PreAuthorize("@authz.staffDeTorneo(#req.torneoId)")
    public ResponseEntity<RegistrarPagoResult> registrarPagoTodas(
            @PathVariable UUID personaId,
            @Valid @RequestBody RegistrarPagoTodasRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        var result = multaService.registrarPagoTodas(
                personaId, req.getTorneoId(), req.getPartidoPagoId(), JwtClaims.cuentaId(jwt));
        return ResponseEntity.ok(RegistrarPagoResult.builder()
                .totalPagado(result.totalPagado())
                .multasPagadasIds(result.multasPagadasIds())
                .build());
    }

    @GetMapping("/jugador/{personaId}/suspensiones")
    @PreAuthorize("@authz.staffDeTorneo(#torneoId)")
    public ResponseEntity<List<MultaDTO>> suspensiones(
            @PathVariable UUID personaId,
            @RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.suspensionesActivas(personaId, torneoId));
    }

    @GetMapping("/partido/{partidoId}/bloqueados")
    @PreAuthorize("@authz.arbitroDePartido(#partidoId)")
    public ResponseEntity<List<?>> bloqueados(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(multaService.jugadoresBloqueados(partidoId));
    }

    @PostMapping("/{multaId}/habilitar-excepcion")
    @PreAuthorize("@authz.staffDeMulta(#multaId)")
    public ResponseEntity<MultaDTO> habilitarExcepcion(
            @PathVariable UUID multaId,
            @Valid @RequestBody HabilitarExcepcionRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.habilitarExcepcion(
                multaId, req.getMotivo(), JwtClaims.cuentaId(jwt)));
    }

    @PostMapping("/{id}/habilitar")
    @PreAuthorize("@authz.staffDeMulta(#id)")
    public ResponseEntity<MultaDTO> habilitarLegacy(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.habilitarExcepcion(
                id, "Habilitacion legacy", JwtClaims.cuentaId(jwt)));
    }
}

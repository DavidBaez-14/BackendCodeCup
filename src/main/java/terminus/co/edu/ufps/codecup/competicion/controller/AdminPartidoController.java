package terminus.co.edu.ufps.codecup.competicion.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.*;
import terminus.co.edu.ufps.codecup.competicion.service.PartidoAdminService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/admin/partidos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
public class AdminPartidoController {

    private final PartidoAdminService partidoAdminService;

    // ── Eventos ─────────────────────────────────────────────────

    @GetMapping("/{partidoId}/eventos")
    public ResponseEntity<List<EventoDTO>> listar(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(partidoAdminService.listarEventos(partidoId));
    }

    @PostMapping("/{partidoId}/eventos")
    public ResponseEntity<EventoDTO> crear(@PathVariable UUID partidoId, @RequestBody EventoRequest req) {
        return ResponseEntity.ok(partidoAdminService.crearEvento(partidoId, req));
    }

    @DeleteMapping("/{partidoId}/eventos/{eventoId}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID partidoId, @PathVariable UUID eventoId) {
        partidoAdminService.eliminarEvento(partidoId, eventoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{partidoId}/cerrar")
    public ResponseEntity<PartidoAdminDTO> cerrar(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(partidoAdminService.cerrarPartido(partidoId));
    }

    // ── Alineación ──────────────────────────────────────────────

    @GetMapping("/{partidoId}/alineacion")
    public ResponseEntity<List<PartidoJugadorDTO>> listarAlineacion(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(partidoAdminService.listarAlineacion(partidoId));
    }

    @PostMapping("/{partidoId}/alineacion")
    public ResponseEntity<Void> agregarJugador(@PathVariable UUID partidoId, @RequestBody AlineacionRequest req) {
        partidoAdminService.agregarJugador(partidoId, req.getCedula(), req.getEquipoTorneoId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{partidoId}/alineacion/{cedula}")
    public ResponseEntity<Void> quitarJugador(@PathVariable UUID partidoId, @PathVariable String cedula) {
        partidoAdminService.quitarJugador(partidoId, cedula);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{partidoId}/alineacion/add-all")
    public ResponseEntity<Void> agregarTodos(@PathVariable UUID partidoId, @RequestBody AddAllRequest req) {
        partidoAdminService.agregarTodosJugadores(partidoId, req.getEquipoTorneoId());
        return ResponseEntity.ok().build();
    }

    // ── WalkOver / Cancelar ────────────────────────────────────

    @PostMapping("/{partidoId}/wo")
    public ResponseEntity<PartidoAdminDTO> declararWO(@PathVariable UUID partidoId, @RequestBody WORequest req) {
        return ResponseEntity.ok(partidoAdminService.declararWO(partidoId, req.getGanadorEquipoTorneoId(), req.getMotivo()));
    }

    @PostMapping("/{partidoId}/cancelar")
    public ResponseEntity<Void> cancelarPartido(@PathVariable UUID partidoId, @RequestBody CancelarRequest req) {
        partidoAdminService.cancelarPartido(partidoId, req.getMotivo());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{partidoId}/cerrar-sin-pago-arbitraje")
    public ResponseEntity<PartidoAdminDTO> cerrarSinPagoArbitraje(
            @PathVariable UUID partidoId,
            @RequestBody SinPagoArbitrajeRequest req) {
        return ResponseEntity.ok(partidoAdminService.cerrarSinPagoArbitraje(
                partidoId, req.getMotivo(), req.getEquipoNoPagoTorneoId()));
    }

    // ── Admin-only ─────────────────────────────────────────────

    @PostMapping("/{partidoId}/reabrir")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PartidoAdminDTO> reabrirPartido(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(partidoAdminService.reabrirPartido(partidoId));
    }
}

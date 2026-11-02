package terminus.co.edu.ufps.codecup.competicion.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.TorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.*;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.service.*;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/admin/torneos")
@RequiredArgsConstructor
public class AdminTorneoController {

    private final TorneoAdminService torneoAdminService;
    private final FixtureService fixtureService;
    private final PartidoAdminService partidoAdminService;
    private final ClasificacionService clasificacionService;
    private final FinanzasConfigService finanzasConfigService;
    private final PremioService premioService;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> crear(@RequestBody CrearTorneoRequest req) {
        return ResponseEntity.ok(torneoAdminService.crear(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<TorneoDTO>> listar() {
        return ResponseEntity.ok(torneoAdminService.listar());
    }

    @PostMapping("/{torneoId}/publicar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> publicar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.publicar(torneoId));
    }

    @PostMapping("/{torneoId}/iniciar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> iniciar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.iniciar(torneoId));
    }

    // ── Configuracion de formato ──────────────────────────────────

    @GetMapping("/{torneoId}/configuracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoConfigDTO> obtenerConfiguracion(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.obtenerConfiguracion(torneoId));
    }

    @PutMapping("/{torneoId}/configuracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoConfigDTO> guardarConfiguracion(
            @PathVariable UUID torneoId,
            @RequestBody GuardarConfigRequest req) {
        return ResponseEntity.ok(torneoAdminService.guardarConfiguracion(torneoId, req));
    }

    // ── Finanzas Config (Inscripcion + Multas) ───────────────────

    @GetMapping("/{torneoId}/finanzas-config")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<FinanzasConfigDTO> obtenerFinanzasConfig(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(finanzasConfigService.obtenerConfig(torneoId));
    }

    @PutMapping("/{torneoId}/inscripcion-config")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionConfigDTO> guardarInscripcionConfig(
            @PathVariable UUID torneoId,
            @RequestBody InscripcionConfigDTO req) {
        return ResponseEntity.ok(finanzasConfigService.guardarInscripcionConfig(torneoId, req));
    }

    @PutMapping("/{torneoId}/multa-config")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<MultaConfigDTO> guardarMultaConfig(
            @PathVariable UUID torneoId,
            @RequestBody MultaConfigDTO req) {
        return ResponseEntity.ok(finanzasConfigService.guardarMultaConfig(torneoId, req));
    }

    // ── Premios ──────────────────────────────────────────────────

    @GetMapping("/{torneoId}/premios")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<PremioDTO>> listarPremios(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(premioService.listarPremios(torneoId));
    }

    @PostMapping("/{torneoId}/premios")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PremioDTO> crearPremio(
            @PathVariable UUID torneoId,
            @RequestBody CrearPremioRequest req) {
        return ResponseEntity.ok(premioService.crearPremio(torneoId, req));
    }

    @PutMapping("/{torneoId}/premios/{torneoPremioId}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PremioDTO> actualizarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId,
            @RequestBody ActualizarPremioRequest req) {
        return ResponseEntity.ok(premioService.actualizarPremio(torneoId, torneoPremioId, req));
    }

    @DeleteMapping("/{torneoId}/premios/{torneoPremioId}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId) {
        premioService.eliminarPremio(torneoId, torneoPremioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{torneoId}/premios/{torneoPremioId}/asignar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR') or hasAuthority('SCOPE_internal')")
    public ResponseEntity<?> asignarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId,
            @RequestBody AsignarPremioRequest req) {
        return ResponseEntity.ok(premioService.asignarPremio(torneoId, torneoPremioId, req));
    }

    @DeleteMapping("/{torneoId}/premios/{torneoPremioId}/asignar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> quitarAsignacion(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId) {
        premioService.quitarAsignacion(torneoId, torneoPremioId);
        return ResponseEntity.noContent().build();
    }

    // ── Cierre de torneo ────────────────────────────────────────

    @PostMapping("/{torneoId}/cerrar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> cerrarTorneo(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.cerrarTorneo(torneoId));
    }

    // ── Inscripciones ─────────────────────────────────────────────

    @GetMapping("/{torneoId}/inscripciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<InscripcionDTO>> inscripciones(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.listarInscripciones(torneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> aprobarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        String admin = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(torneoAdminService.aprobarInscripcion(torneoId, equipoTorneoId, admin));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> rechazarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req) {
        return ResponseEntity.ok(torneoAdminService.rechazarInscripcion(torneoId, equipoTorneoId, req.getMotivo()));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/habilitar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> habilitarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId) {
        return ResponseEntity.ok(torneoAdminService.habilitarInscripcion(torneoId, equipoTorneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/expulsar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> expulsarEquipo(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        String admin = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(torneoAdminService.expulsarEquipo(torneoId, equipoTorneoId, req.getMotivo(), admin));
    }

    // ── Fixture ──────────────────────────────────────────────────

    @PostMapping("/{torneoId}/fixture")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<PartidoAdminDTO>> generarFixture(@PathVariable UUID torneoId) {
        List<Partido> creados = fixtureService.generar(torneoId);
        return ResponseEntity.ok(creados.stream().map(partidoAdminService::toPartidoDTO).toList());
    }

    @DeleteMapping("/{torneoId}/fixture")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> borrarFixture(@PathVariable UUID torneoId) {
        torneoAdminService.borrarFixture(torneoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{torneoId}/partidos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<PartidoAdminDTO>> partidos(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(partidoAdminService.listarPorTorneo(torneoId));
    }

    @GetMapping("/{torneoId}/clasificacion")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<Map<String, List<PosicionGrupoDTO>>> clasificacion(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(clasificacionService.calcularPorGrupo(torneoId));
    }
}

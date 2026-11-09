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
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

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

    /** Spec 0024: cualquier cuenta registrada crea un torneo y queda como su admin. */
    @PostMapping
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<TorneoDTO> crear(@RequestBody CrearTorneoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(torneoAdminService.crear(req, JwtClaims.cuentaId(jwt)));
    }

    /** Specs 0024 y 0025: los torneos donde quien llama es admin o árbitro. */
    @GetMapping
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<TorneoDTO>> listar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(torneoAdminService.listarDeStaff(JwtClaims.cuentaId(jwt)));
    }

    @PostMapping("/{torneoId}/publicar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoDTO> publicar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.publicar(torneoId));
    }

    @PostMapping("/{torneoId}/iniciar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoDTO> iniciar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.iniciar(torneoId));
    }

    // ── Configuracion de formato ──────────────────────────────────

    @GetMapping("/{torneoId}/configuracion")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoConfigDTO> obtenerConfiguracion(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.obtenerConfiguracion(torneoId));
    }

    @PutMapping("/{torneoId}/configuracion")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoConfigDTO> guardarConfiguracion(
            @PathVariable UUID torneoId,
            @RequestBody GuardarConfigRequest req) {
        return ResponseEntity.ok(torneoAdminService.guardarConfiguracion(torneoId, req));
    }

    // ── Finanzas Config (Inscripcion + Multas) ───────────────────

    @GetMapping("/{torneoId}/finanzas-config")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<FinanzasConfigDTO> obtenerFinanzasConfig(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(finanzasConfigService.obtenerConfig(torneoId));
    }

    @PutMapping("/{torneoId}/inscripcion-config")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InscripcionConfigDTO> guardarInscripcionConfig(
            @PathVariable UUID torneoId,
            @RequestBody InscripcionConfigDTO req) {
        return ResponseEntity.ok(finanzasConfigService.guardarInscripcionConfig(torneoId, req));
    }

    /** Spec 0023: el torneo exige (o no) documento a sus miembros. */
    @PutMapping("/{torneoId}/exige-documento")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoDTO> cambiarExigeDocumento(@PathVariable UUID torneoId,
                                                           @RequestBody java.util.Map<String, Boolean> req) {
        return ResponseEntity.ok(torneoAdminService.cambiarExigeDocumento(torneoId,
                Boolean.TRUE.equals(req.get("exigeDocumento"))));
    }

    @PutMapping("/{torneoId}/multa-config")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<MultaConfigDTO> guardarMultaConfig(
            @PathVariable UUID torneoId,
            @RequestBody MultaConfigDTO req) {
        return ResponseEntity.ok(finanzasConfigService.guardarMultaConfig(torneoId, req));
    }

    // ── Premios ──────────────────────────────────────────────────

    @GetMapping("/{torneoId}/premios")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<List<PremioDTO>> listarPremios(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(premioService.listarPremios(torneoId));
    }

    @PostMapping("/{torneoId}/premios")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<PremioDTO> crearPremio(
            @PathVariable UUID torneoId,
            @RequestBody CrearPremioRequest req) {
        return ResponseEntity.ok(premioService.crearPremio(torneoId, req));
    }

    @PutMapping("/{torneoId}/premios/{torneoPremioId}")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<PremioDTO> actualizarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId,
            @RequestBody ActualizarPremioRequest req) {
        return ResponseEntity.ok(premioService.actualizarPremio(torneoId, torneoPremioId, req));
    }

    @DeleteMapping("/{torneoId}/premios/{torneoPremioId}")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<Void> eliminarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId) {
        premioService.eliminarPremio(torneoId, torneoPremioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{torneoId}/premios/{torneoPremioId}/asignar")
    @PreAuthorize("hasAuthority('SCOPE_internal') or @authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<?> asignarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId,
            @RequestBody AsignarPremioRequest req) {
        return ResponseEntity.ok(premioService.asignarPremio(torneoId, torneoPremioId, req));
    }

    @DeleteMapping("/{torneoId}/premios/{torneoPremioId}/asignar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<Void> quitarAsignacion(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId) {
        premioService.quitarAsignacion(torneoId, torneoPremioId);
        return ResponseEntity.noContent().build();
    }

    // ── Cierre de torneo ────────────────────────────────────────

    @PostMapping("/{torneoId}/cerrar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoDTO> cerrarTorneo(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.cerrarTorneo(torneoId));
    }

    // ── Inscripciones ─────────────────────────────────────────────

    @GetMapping("/{torneoId}/inscripciones")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<List<InscripcionDTO>> inscripciones(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.listarInscripciones(torneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/aprobar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InscripcionDTO> aprobarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID admin = JwtClaims.cuentaId(jwt);
        return ResponseEntity.ok(torneoAdminService.aprobarInscripcion(torneoId, equipoTorneoId, admin));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/rechazar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InscripcionDTO> rechazarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req) {
        return ResponseEntity.ok(torneoAdminService.rechazarInscripcion(torneoId, equipoTorneoId, req.getMotivo()));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/habilitar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InscripcionDTO> habilitarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId) {
        return ResponseEntity.ok(torneoAdminService.habilitarInscripcion(torneoId, equipoTorneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/expulsar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InscripcionDTO> expulsarEquipo(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        UUID admin = JwtClaims.cuentaId(jwt);
        return ResponseEntity.ok(torneoAdminService.expulsarEquipo(torneoId, equipoTorneoId, req.getMotivo(), admin));
    }

    // ── Fixture ──────────────────────────────────────────────────

    @PostMapping("/{torneoId}/fixture")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<List<PartidoAdminDTO>> generarFixture(@PathVariable UUID torneoId) {
        List<Partido> creados = fixtureService.generar(torneoId);
        return ResponseEntity.ok(creados.stream().map(partidoAdminService::toPartidoDTO).toList());
    }

    @DeleteMapping("/{torneoId}/fixture")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<Void> borrarFixture(@PathVariable UUID torneoId) {
        torneoAdminService.borrarFixture(torneoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{torneoId}/partidos")
    @PreAuthorize("@authz.staffDeTorneo(#torneoId)")
    public ResponseEntity<List<PartidoAdminDTO>> partidos(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(partidoAdminService.listarPorTorneo(torneoId));
    }

    @GetMapping("/{torneoId}/clasificacion")
    @PreAuthorize("@authz.staffDeTorneo(#torneoId)")
    public ResponseEntity<Map<String, List<PosicionGrupoDTO>>> clasificacion(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(clasificacionService.calcularPorGrupo(torneoId));
    }
}

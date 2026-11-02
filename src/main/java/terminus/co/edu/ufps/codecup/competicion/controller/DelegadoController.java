package terminus.co.edu.ufps.codecup.competicion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionInfoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.ActualizarCamisetaRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.AgregarMiembroRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.codecup.competicion.service.DelegadoService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/delegado")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELEGADO')")
public class DelegadoController {

    private final DelegadoService delegadoService;

    @PostMapping("/equipos")
    public ResponseEntity<EquipoDTO> crearEquipo(
            @RequestBody CrearEquipoRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.crearEquipo(jwt.getClaimAsString("cedula"), req));
    }

    @GetMapping("/equipos/mi-equipo")
    public ResponseEntity<EquipoDTO> miEquipo(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.miEquipo(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/torneos/disponibles")
    public ResponseEntity<List<TorneoDisponibleDTO>> torneos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.torneosDisponibles(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/torneos/{torneoId}/inscripcion-info")
    public ResponseEntity<InscripcionInfoDTO> inscripcionInfo(
            @PathVariable UUID torneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.obtenerInscripcionInfo(
                jwt.getClaimAsString("cedula"), torneoId));
    }

    @PostMapping("/torneos/{torneoId}/inscribir")
    public ResponseEntity<InscripcionDTO> inscribir(
            @PathVariable UUID torneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.inscribir(jwt.getClaimAsString("cedula"), torneoId));
    }

    @GetMapping("/inscripciones/mias")
    public ResponseEntity<List<InscripcionDTO>> misInscripciones(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.misInscripciones(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/equipo-torneo/{equipoTorneoId}/miembros")
    public ResponseEntity<List<MiembroEquipoDTO>> miembros(
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.miembros(jwt.getClaimAsString("cedula"), equipoTorneoId));
    }

    // ── HU44 — Agregar miembro ──────────────────────────────
    @PostMapping("/equipo-torneo/{equipoTorneoId}/miembros")
    public ResponseEntity<MiembroEquipoDTO> agregarMiembro(
            @PathVariable UUID equipoTorneoId,
            @RequestBody @Valid AgregarMiembroRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                delegadoService.agregarMiembro(jwt.getClaimAsString("cedula"), equipoTorneoId, req));
    }

    // ── HU44 — Remover miembro ──────────────────────────────
    @PostMapping("/equipo-torneo/{equipoTorneoId}/miembros/{cedula}/remover")
    public ResponseEntity<MiembroEquipoDTO> removerMiembro(
            @PathVariable UUID equipoTorneoId,
            @PathVariable String cedula,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                delegadoService.removerMiembro(jwt.getClaimAsString("cedula"), equipoTorneoId, cedula));
    }

    // ── HU44 — Actualizar número de camiseta ──────────────
    @PatchMapping("/equipo-torneo/{equipoTorneoId}/miembros/{cedula}/camiseta")
    public ResponseEntity<MiembroEquipoDTO> actualizarCamiseta(
            @PathVariable UUID equipoTorneoId,
            @PathVariable String cedula,
            @RequestBody @Valid ActualizarCamisetaRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                delegadoService.actualizarCamiseta(
                        jwt.getClaimAsString("cedula"), equipoTorneoId, cedula, req.getNumeroCamiseta()));
    }
}

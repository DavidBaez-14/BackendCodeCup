package terminus.co.edu.ufps.codecup.competicion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionInfoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.ActualizarCamisetaRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.AgregarMiembroRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.InscribirEquipoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.codecup.competicion.service.DelegadoService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/delegado")
@RequiredArgsConstructor
@PreAuthorize("@authz.registrado()")
public class DelegadoController {

    private final DelegadoService delegadoService;

    @PostMapping("/equipos")
    public ResponseEntity<EquipoDTO> crearEquipo(
            @RequestBody CrearEquipoRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.crearEquipo(JwtClaims.personaId(jwt), req));
    }

    /** Spec 0026: todos sus equipos base, el más reciente primero. */
    @GetMapping("/equipos")
    public ResponseEntity<List<EquipoDTO>> misEquipos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.misEquiposBase(JwtClaims.personaId(jwt)));
    }

    /** Compatibilidad con la web: el equipo base más reciente. */
    @GetMapping("/equipos/mi-equipo")
    public ResponseEntity<EquipoDTO> miEquipo(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.miEquipo(JwtClaims.personaId(jwt)));
    }

    @GetMapping("/torneos/disponibles")
    public ResponseEntity<List<TorneoDisponibleDTO>> torneos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.torneosDisponibles(JwtClaims.personaId(jwt)));
    }

    @GetMapping("/torneos/{torneoId}/inscripcion-info")
    public ResponseEntity<InscripcionInfoDTO> inscripcionInfo(
            @PathVariable UUID torneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.obtenerInscripcionInfo(
                JwtClaims.personaId(jwt), torneoId));
    }

    /**
     * Spec 0026: sin rol global. Cuerpo opcional {@code {equipoId?, nombreEquipo?}}; sin cuerpo se
     * inscribe el único equipo de la persona (400 EQUIPO_REQUERIDO si tiene varios o ninguno).
     */
    @PostMapping("/torneos/{torneoId}/inscribir")
    public ResponseEntity<InscripcionDTO> inscribir(
            @PathVariable UUID torneoId,
            @RequestBody(required = false) InscribirEquipoRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.inscribir(JwtClaims.personaId(jwt), torneoId, req));
    }

    @GetMapping("/inscripciones/mias")
    public ResponseEntity<List<InscripcionDTO>> misInscripciones(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.misInscripciones(JwtClaims.personaId(jwt)));
    }

    @GetMapping("/equipo-torneo/{equipoTorneoId}/miembros")
    public ResponseEntity<List<MiembroEquipoDTO>> miembros(
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.miembros(JwtClaims.personaId(jwt), equipoTorneoId));
    }

    // ── HU44 — Agregar miembro ──────────────────────────────
    @PostMapping("/equipo-torneo/{equipoTorneoId}/miembros")
    public ResponseEntity<MiembroEquipoDTO> agregarMiembro(
            @PathVariable UUID equipoTorneoId,
            @RequestBody @Valid AgregarMiembroRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                delegadoService.agregarMiembro(JwtClaims.personaId(jwt), equipoTorneoId, req));
    }

    // ── HU44 — Remover miembro ──────────────────────────────
    @PostMapping("/equipo-torneo/{equipoTorneoId}/miembros/{personaId}/remover")
    public ResponseEntity<MiembroEquipoDTO> removerMiembro(
            @PathVariable UUID equipoTorneoId,
            @PathVariable UUID personaId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                delegadoService.removerMiembro(JwtClaims.personaId(jwt), equipoTorneoId, personaId));
    }

    // ── HU44 — Actualizar número de camiseta ──────────────
    @PatchMapping("/equipo-torneo/{equipoTorneoId}/miembros/{personaId}/camiseta")
    public ResponseEntity<MiembroEquipoDTO> actualizarCamiseta(
            @PathVariable UUID equipoTorneoId,
            @PathVariable UUID personaId,
            @RequestBody @Valid ActualizarCamisetaRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                delegadoService.actualizarCamiseta(
                        JwtClaims.personaId(jwt), equipoTorneoId, personaId, req.getNumeroCamiseta()));
    }
}

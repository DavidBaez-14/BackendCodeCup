package terminus.co.edu.ufps.codecup.competicion.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.SolicitudDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.SolicitudRequestDTO;
import terminus.co.edu.ufps.codecup.competicion.service.SolicitudService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;

    @PostMapping
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<SolicitudDTO> crear(
            @RequestBody SolicitudRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        UUID personaId = JwtClaims.personaId(jwt);
        return ResponseEntity.ok(solicitudService.crear(personaId, req.getEquipoTorneoId(), req.getEnlaceToken()));
    }

    @GetMapping("/mis-solicitudes")
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<List<SolicitudDTO>> misSolicitudes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.listarSolicitudesJugador(JwtClaims.personaId(jwt)));
    }

    @GetMapping
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<SolicitudDTO>> listarSolicitudes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.listarSolicitudesDelegado(JwtClaims.personaId(jwt)));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<SolicitudDTO>> listarPendientes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.listarPendientesDelegado(JwtClaims.personaId(jwt)));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<SolicitudDTO> aprobar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.aprobar(id, JwtClaims.personaId(jwt)));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<SolicitudDTO> rechazar(
            @PathVariable UUID id,
            @RequestBody RechazoRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.rechazar(id, req.getMotivo(), JwtClaims.personaId(jwt)));
    }
}

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
        String cedula = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(solicitudService.crear(cedula, req.getEquipoTorneoId()));
    }

    @GetMapping("/mis-solicitudes")
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<List<SolicitudDTO>> misSolicitudes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.listarSolicitudesJugador(jwt.getClaimAsString("cedula")));
    }

    @GetMapping
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<List<SolicitudDTO>> listarSolicitudes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.listarSolicitudesDelegado(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<List<SolicitudDTO>> listarPendientes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.listarPendientesDelegado(jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<SolicitudDTO> aprobar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.aprobar(id, jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<SolicitudDTO> rechazar(
            @PathVariable UUID id,
            @RequestBody RechazoRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(solicitudService.rechazar(id, req.getMotivo(), jwt.getClaimAsString("cedula")));
    }
}

package terminus.co.edu.ufps.codecup.finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.finanzas.dto.*;
import terminus.co.edu.ufps.codecup.finanzas.service.ComprobanteService;
import terminus.co.edu.ufps.codecup.finanzas.service.StorageService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finanzas/comprobantes")
@RequiredArgsConstructor
public class ComprobanteController {

    private final ComprobanteService comprobanteService;
    private final StorageService storageService;

    /** Spec 0001: solo para una inscripción propia y pendiente de pago. */
    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<UploadUrlResponse> solicitarUploadUrl(
            @Valid @RequestBody SolicitudUploadUrlRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        comprobanteService.validarSubidaPropia(req.getEquipoTorneoId(), jwt.getClaimAsString("cedula"));
        var result = storageService.generarUploadUrl(req.getEquipoTorneoId());
        return ResponseEntity.ok(UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .fileKey(result.fileKey())
                .build());
    }

    /** Spec 0001: el admin descarga cualquiera; el delegado, solo los de su equipo (403 si no). */
    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DELEGADO')")
    public ResponseEntity<Map<String, String>> downloadUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()));
        String fileKey = comprobanteService.archivoParaDescarga(id, jwt.getClaimAsString("cedula"), esAdmin);
        return ResponseEntity.ok(Map.of("url", storageService.generarDownloadUrl(fileKey)));
    }

    @PostMapping
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<ComprobanteDTO> subir(
            @Valid @RequestBody SubirComprobanteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.subir(req, jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/mis-comprobantes")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<List<ComprobanteDTO>> misComprobantes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.misComprobantes(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<ComprobanteDTO>> listarPendientes() {
        return ResponseEntity.ok(comprobanteService.listarPendientes());
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ComprobanteDTO> aprobar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.aprobar(id, jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ComprobanteDTO> rechazar(
            @PathVariable UUID id,
            @Valid @RequestBody RechazoComprobanteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.rechazar(id, req.getMotivo(), jwt.getClaimAsString("cedula")));
    }
}

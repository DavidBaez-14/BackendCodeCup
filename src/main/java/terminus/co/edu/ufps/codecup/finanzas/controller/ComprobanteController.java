package terminus.co.edu.ufps.codecup.finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.finanzas.dto.*;
import terminus.co.edu.ufps.codecup.finanzas.service.ComprobanteService;
import terminus.co.edu.ufps.codecup.finanzas.service.StorageService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finanzas/comprobantes")
@RequiredArgsConstructor
public class ComprobanteController {

    private final ComprobanteService comprobanteService;
    private final StorageService storageService;

    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<UploadUrlResponse> solicitarUploadUrl(
            @Valid @RequestBody SolicitudUploadUrlRequest req) {
        var result = storageService.generarUploadUrl(req.getEquipoTorneoId());
        return ResponseEntity.ok(UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .fileKey(result.fileKey())
                .build());
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DELEGADO')")
    public ResponseEntity<?> downloadUrl(@PathVariable UUID id) {
        var comprobante = comprobanteService.obtenerComprobante(id);
        if (comprobante == null) {
            return ResponseEntity.notFound().build();
        }
        String url = storageService.generarDownloadUrl(comprobante.getUrlArchivo());
        return ResponseEntity.ok(java.util.Map.of("url", url));
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

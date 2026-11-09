package terminus.co.edu.ufps.codecup.finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.finanzas.dto.*;
import terminus.co.edu.ufps.codecup.finanzas.service.ComprobanteService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;
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
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<UploadUrlResponse> solicitarUploadUrl(
            @Valid @RequestBody SolicitudUploadUrlRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        comprobanteService.validarSubidaPropia(req.getEquipoTorneoId(), JwtClaims.personaId(jwt));
        var result = storageService.generarUploadUrl(req.getEquipoTorneoId());
        return ResponseEntity.ok(UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .fileKey(result.fileKey())
                .build());
    }

    /**
     * Spec 0001: el delegado, solo los de su equipo. Spec 0024: el admin, solo los de los torneos
     * que administra. 403 COMPROBANTE_AJENO si no.
     */
    @GetMapping("/{id}/download-url")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<Map<String, String>> downloadUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String fileKey = comprobanteService.archivoParaDescarga(id, JwtClaims.personaId(jwt), JwtClaims.cuentaId(jwt));
        return ResponseEntity.ok(Map.of("url", storageService.generarDownloadUrl(fileKey)));
    }

    @PostMapping
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<ComprobanteDTO> subir(
            @Valid @RequestBody SubirComprobanteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.subir(req, JwtClaims.personaId(jwt), JwtClaims.cuentaId(jwt)));
    }

    @GetMapping("/mis-comprobantes")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<ComprobanteDTO>> misComprobantes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.misComprobantes(JwtClaims.cuentaId(jwt)));
    }

    /** Spec 0024: solo de los torneos que administra quien llama; {@code torneoId} filtra uno. */
    @GetMapping("/pendientes")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<ComprobanteDTO>> listarPendientes(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID torneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.listarPendientes(JwtClaims.cuentaId(jwt), torneoId));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("@authz.adminDeComprobante(#id)")
    public ResponseEntity<ComprobanteDTO> aprobar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.aprobar(id, JwtClaims.cuentaId(jwt)));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("@authz.adminDeComprobante(#id)")
    public ResponseEntity<ComprobanteDTO> rechazar(
            @PathVariable UUID id,
            @Valid @RequestBody RechazoComprobanteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.rechazar(id, req.getMotivo(), JwtClaims.cuentaId(jwt)));
    }
}

package terminus.co.edu.ufps.codecup.competicion.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.VinculoPendienteDTO;
import terminus.co.edu.ufps.codecup.competicion.service.VinculacionService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/**
 * Bandeja de solicitudes de vínculo (spec 0023): alguien se registró con el documento de un
 * miembro sin cuenta y pide quedarse con su historial. La resuelve su delegado o un admin.
 */
@RestController
@RequestMapping("/api/supercopa/vinculos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DELEGADO','ADMINISTRADOR')")
public class VinculoController {

    private final VinculacionService vinculacionService;

    @GetMapping("/pendientes")
    public ResponseEntity<List<VinculoPendienteDTO>> pendientes(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return ResponseEntity.ok(vinculacionService.pendientes(JwtClaims.personaId(jwt), esAdmin(auth)));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<Void> aprobar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        vinculacionService.aprobar(id, JwtClaims.personaId(jwt), JwtClaims.cuentaId(jwt), esAdmin(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable UUID id,
                                         @RequestBody(required = false) Map<String, String> req,
                                         @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        String motivo = req != null ? req.get("motivo") : null;
        vinculacionService.rechazar(id, motivo, JwtClaims.personaId(jwt), JwtClaims.cuentaId(jwt), esAdmin(auth));
        return ResponseEntity.noContent().build();
    }

    private static boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()));
    }
}

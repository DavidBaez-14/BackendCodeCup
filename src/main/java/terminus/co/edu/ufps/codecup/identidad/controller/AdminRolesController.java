package terminus.co.edu.ufps.codecup.identidad.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.identidad.dto.AsignarRolRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.CuentaRolPendienteDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.MotivoRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.RevocarRolRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.service.AdminRolesService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminRolesController {

    private final AdminRolesService adminRolesService;

    @GetMapping("/pendientes")
    public ResponseEntity<List<CuentaRolPendienteDTO>> listarPendientes(
            @RequestParam(required = false) EstadoRol estado) {
        return ResponseEntity.ok(adminRolesService.listarPendientes(estado));
    }

    @PostMapping("/{cuentaRolId}/aprobar")
    public ResponseEntity<Void> aprobar(@PathVariable UUID cuentaRolId) {
        adminRolesService.aprobar(cuentaRolId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cuenta/{cuentaId}/aprobar-todos")
    public ResponseEntity<Void> aprobarTodos(@PathVariable UUID cuentaId) {
        adminRolesService.aprobarTodos(cuentaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cuenta/{cuentaId}/rechazar-todos")
    public ResponseEntity<Void> rechazarTodos(@PathVariable UUID cuentaId, @RequestBody MotivoRequestDTO req) {
        adminRolesService.rechazarTodos(cuentaId, req.getMotivo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cuentaRolId}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable UUID cuentaRolId, @RequestBody MotivoRequestDTO req) {
        adminRolesService.rechazar(cuentaRolId, req.getMotivo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/asignar")
    public ResponseEntity<Void> asignarRol(@Valid @RequestBody AsignarRolRequestDTO req,
                                           @AuthenticationPrincipal Jwt jwt) {
        adminRolesService.asignarRol(req.getCuentaId(), req.getRol(), req.getMotivo(), JwtClaims.cuentaId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revocar")
    public ResponseEntity<Void> revocarRol(@Valid @RequestBody RevocarRolRequestDTO req,
                                           @AuthenticationPrincipal Jwt jwt) {
        adminRolesService.revocarRol(req.getCuentaId(), req.getRol(), req.getMotivo(), JwtClaims.cuentaId(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Ruta heredada de MS1. Ahora bloquea la cuenta: el backend no borra usuarios del proveedor. */
    @DeleteMapping("/cuenta/{cuentaId}")
    public ResponseEntity<Void> eliminarCuenta(@PathVariable UUID cuentaId,
                                               @Valid @RequestBody MotivoRequestDTO req,
                                               @AuthenticationPrincipal Jwt jwt) {
        adminRolesService.eliminarCuenta(cuentaId, req.getMotivo(), JwtClaims.cuentaId(jwt));
        return ResponseEntity.noContent().build();
    }
}

package terminus.co.edu.ufps.codecup.identidad.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.service.InvitacionService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/** Reemplaza a POST /api/admin/cuentas: el admin invita y la persona se registra sola. */
@RestController
@RequestMapping("/api/admin/invitaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminInvitacionesController {

    private final InvitacionService invitacionService;

    @PostMapping
    public ResponseEntity<InvitacionDTO> crear(@Valid @RequestBody InvitacionRequestDTO req,
                                               @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitacionService.crear(req, JwtClaims.cuentaId(jwt)));
    }

    @GetMapping
    public ResponseEntity<List<InvitacionDTO>> listar(@RequestParam(required = false) EstadoInvitacion estado) {
        return ResponseEntity.ok(invitacionService.listar(estado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revocar(@PathVariable UUID id) {
        invitacionService.revocar(id);
        return ResponseEntity.noContent().build();
    }
}

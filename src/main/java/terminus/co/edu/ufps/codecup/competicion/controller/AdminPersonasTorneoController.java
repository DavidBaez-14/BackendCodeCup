package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.InvitarATorneoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.RolTorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.service.RolesTorneoService;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/**
 * Personas de un torneo (spec 0024, pantalla N-24): sus admins y árbitros, y las invitaciones
 * pendientes. Solo para admins de ese torneo.
 */
@RestController
@RequestMapping("/api/supercopa/admin/torneos/{torneoId}")
@RequiredArgsConstructor
@Tag(name = "Creador")
public class AdminPersonasTorneoController {

    private final RolesTorneoService rolesTorneoService;

    @GetMapping("/roles")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<List<RolTorneoDTO>> roles(@PathVariable UUID torneoId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(rolesTorneoService.listar(torneoId, JwtClaims.cuentaId(jwt)));
    }

    /** 409 ULTIMO_ADMIN_DEL_TORNEO si es el último admin. */
    @DeleteMapping("/roles/{rolId}")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<Void> quitarRol(@PathVariable UUID torneoId, @PathVariable UUID rolId,
                                          @AuthenticationPrincipal Jwt jwt) {
        rolesTorneoService.quitar(torneoId, rolId, JwtClaims.cuentaId(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * Spec 0025: da o quita a un árbitro del torneo el permiso de operar todos sus partidos.
     * Cuerpo {@code {"todosLosPartidos": true}}. 404 NO_ES_ARBITRO_DEL_TORNEO.
     */
    @PutMapping("/arbitros/{cuentaId}/todos-los-partidos")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<RolTorneoDTO> todosLosPartidos(@PathVariable UUID torneoId, @PathVariable UUID cuentaId,
                                                         @RequestBody java.util.Map<String, Boolean> req,
                                                         @AuthenticationPrincipal Jwt jwt) {
        Boolean valor = req.get("todosLosPartidos");
        if (valor == null) {
            throw terminus.co.edu.ufps.codecup.shared.error.ApiException.badRequest("VALIDACION", "Indica todosLosPartidos (true o false).");
        }
        return ResponseEntity.ok(rolesTorneoService.cambiarTodosLosPartidos(torneoId, cuentaId, valor, JwtClaims.cuentaId(jwt)));
    }

    /** Invita a un co-admin o árbitro. Devuelve el enlace para compartir. */
    @PostMapping("/invitaciones")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InvitacionDTO> invitar(@PathVariable UUID torneoId,
                                                 @Valid @RequestBody InvitarATorneoRequest req,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rolesTorneoService.invitar(torneoId, req.getRol(), req.getCorreo(), JwtClaims.cuentaId(jwt)));
    }

    @GetMapping("/invitaciones")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<List<InvitacionDTO>> invitaciones(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(rolesTorneoService.invitacionesPendientes(torneoId));
    }

    @DeleteMapping("/invitaciones/{invitacionId}")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<Void> revocarInvitacion(@PathVariable UUID torneoId, @PathVariable UUID invitacionId) {
        rolesTorneoService.revocarInvitacion(torneoId, invitacionId);
        return ResponseEntity.noContent().build();
    }
}

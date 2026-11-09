package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.codecup.competicion.service.CupoEquipoService;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/** Equipos que el admin suma por invitación y reasignación del delegado (spec 0026). */
@RestController
@RequestMapping("/api/supercopa/admin/torneos/{torneoId}")
@RequiredArgsConstructor
@Tag(name = "Creador")
public class AdminCuposController {

    private final CupoEquipoService cupoEquipoService;

    /**
     * Camino (b): el cupo de un equipo. Cuerpo {@code {"nombreEquipo": "…", "correo": "…"?}}.
     * Devuelve la invitación con su enlace; quien la acepte queda como delegado de ese equipo.
     */
    @PostMapping("/cupos")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InvitacionDTO> crearCupo(@PathVariable UUID torneoId, @RequestBody Map<String, String> req,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cupoEquipoService.crearCupo(
                torneoId, req.get("nombreEquipo"), vacioANull(req.get("correo")), JwtClaims.cuentaId(jwt)));
    }

    /** Cuerpo {@code {"personaId": "…"}}: un miembro activo del plantel con cuenta. */
    @PutMapping("/inscripciones/{equipoTorneoId}/delegado")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<InscripcionDTO> reasignarDelegado(@PathVariable UUID torneoId, @PathVariable UUID equipoTorneoId,
                                                            @RequestBody Map<String, UUID> req,
                                                            @AuthenticationPrincipal Jwt jwt) {
        UUID personaId = req.get("personaId");
        if (personaId == null) {
            throw ApiException.badRequest("VALIDACION", "Indica el nuevo delegado (personaId).");
        }
        return ResponseEntity.ok(cupoEquipoService.reasignarDelegado(torneoId, equipoTorneoId, personaId, JwtClaims.cuentaId(jwt)));
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

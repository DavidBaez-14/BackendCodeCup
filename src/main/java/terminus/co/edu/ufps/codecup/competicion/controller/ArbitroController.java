package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroPartidoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.PlanillaDTO;
import terminus.co.edu.ufps.codecup.competicion.service.ArbitrajeService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/**
 * Sección Árbitro (spec 0025, MOV-0009). Las acciones de la planilla (alineación, eventos, cerrar,
 * W.O., cancelar) siguen en {@code /api/supercopa/admin/partidos/{id}/...}, con el mismo permiso.
 */
@RestController
@RequestMapping("/api/supercopa/arbitro")
@RequiredArgsConstructor
@Tag(name = "Árbitro")
public class ArbitroController {

    private final ArbitrajeService arbitrajeService;

    /** Los partidos que arbitra quien llama, en orden de fecha. Vacía si no tiene ninguno. */
    @GetMapping("/partidos")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<ArbitroPartidoDTO>> misPartidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(arbitrajeService.misPartidos(JwtClaims.cuentaId(jwt), desde, hasta));
    }

    /** La planilla completa. 403 NO_ARBITRO_DEL_PARTIDO si no lo puede arbitrar. */
    @GetMapping("/partidos/{partidoId}/planilla")
    @PreAuthorize("@authz.arbitroDePartido(#partidoId)")
    public ResponseEntity<PlanillaDTO> planilla(@PathVariable UUID partidoId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(arbitrajeService.planilla(partidoId, JwtClaims.cuentaId(jwt)));
    }
}

package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroAsignadoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroPartidoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ProgramarPartidoRequest;
import terminus.co.edu.ufps.codecup.competicion.service.ArbitrajeService;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/** Árbitros y programación de un partido (spec 0025): lo maneja el admin del torneo. */
@RestController
@RequestMapping("/api/supercopa/admin/partidos/{partidoId}")
@RequiredArgsConstructor
@Tag(name = "Creador")
public class AdminArbitrajePartidoController {

    private final ArbitrajeService arbitrajeService;

    /** Los árbitros asignados. Lo ven el admin y quien arbitra el partido. */
    @GetMapping("/arbitros")
    @PreAuthorize("@authz.arbitroDePartido(#partidoId)")
    public ResponseEntity<List<ArbitroAsignadoDTO>> arbitros(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(arbitrajeService.arbitrosDe(partidoId));
    }

    /** Cuerpo {@code {"cuentaId": "…"}}. 409 NO_ES_ARBITRO_DEL_TORNEO o ARBITRO_EN_PLANTILLA. */
    @PostMapping("/arbitros")
    @PreAuthorize("@authz.adminDePartido(#partidoId)")
    public ResponseEntity<List<ArbitroAsignadoDTO>> asignar(@PathVariable UUID partidoId,
                                                            @RequestBody Map<String, UUID> req,
                                                            @AuthenticationPrincipal Jwt jwt) {
        UUID cuentaId = req.get("cuentaId");
        if (cuentaId == null) {
            throw ApiException.badRequest("VALIDACION", "Indica el árbitro (cuentaId).");
        }
        return ResponseEntity.ok(arbitrajeService.asignar(partidoId, cuentaId, JwtClaims.cuentaId(jwt)));
    }

    @DeleteMapping("/arbitros/{cuentaId}")
    @PreAuthorize("@authz.adminDePartido(#partidoId)")
    public ResponseEntity<List<ArbitroAsignadoDTO>> quitar(@PathVariable UUID partidoId, @PathVariable UUID cuentaId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(arbitrajeService.quitar(partidoId, cuentaId, JwtClaims.cuentaId(jwt)));
    }

    /** Cuándo y dónde se juega. 409 PARTIDO_CERRADO si ya terminó. */
    @PutMapping("/programacion")
    @PreAuthorize("@authz.adminDePartido(#partidoId)")
    public ResponseEntity<ArbitroPartidoDTO> programar(@PathVariable UUID partidoId,
                                                       @Valid @RequestBody ProgramarPartidoRequest req) {
        return ResponseEntity.ok(arbitrajeService.programar(partidoId, req));
    }
}

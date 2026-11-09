package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.CreadorTorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.service.CreadorService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/** Sección Creador (spec 0024, MOV-0008): cada persona ve solo los torneos que administra. */
@RestController
@RequestMapping("/api/supercopa/creador")
@RequiredArgsConstructor
@Tag(name = "Creador")
public class CreadorController {

    private final CreadorService creadorService;

    /** Los torneos que administra quien llama, con sus pendientes. Lista vacía si no administra ninguno. */
    @GetMapping("/torneos")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<CreadorTorneoDTO>> misTorneos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(creadorService.misTorneos(JwtClaims.cuentaId(jwt)));
    }
}

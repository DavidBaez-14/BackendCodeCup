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
import terminus.co.edu.ufps.codecup.competicion.dto.jugador.MiEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.service.MisEquiposService;
import terminus.co.edu.ufps.codecup.shared.security.JwtClaims;

/** Lo de quien llama, sin importar el rol (spec 0026). */
@RestController
@RequestMapping("/api/supercopa/yo")
@RequiredArgsConstructor
@Tag(name = "Jugador")
public class YoController {

    private final MisEquiposService misEquiposService;

    /**
     * Sus equipos en cualquier torneo, como jugador o delegado, con el estado del trámite y el
     * próximo partido. "Tu próximo partido" de Inicio es el más cercano de esta lista.
     */
    @GetMapping("/equipos")
    @PreAuthorize("@authz.registrado()")
    public ResponseEntity<List<MiEquipoDTO>> misEquipos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(misEquiposService.misEquipos(JwtClaims.personaId(jwt)));
    }
}

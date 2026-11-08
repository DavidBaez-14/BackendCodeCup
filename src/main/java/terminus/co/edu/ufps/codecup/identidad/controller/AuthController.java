package terminus.co.edu.ufps.codecup.identidad.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.identidad.dto.DocumentoRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.MeResponseDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.RegistroRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.SolicitarRolRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.SolicitudRolResponseDTO;
import terminus.co.edu.ufps.codecup.identidad.service.AuthService;

/**
 * El cliente inicia sesión directo con Supabase y manda su access token en
 * {@code Authorization: Bearer}. Aquí no hay login, refresh ni emisión de tokens.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    /** Cuenta de CodeCup de quien llama. 404 CUENTA_NO_REGISTRADA si aún debe completar el registro. */
    @GetMapping("/me")
    public ResponseEntity<MeResponseDTO> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.me(jwt.getSubject()));
    }

    /** Completa el registro tras el primer login: crea persona, cuenta y rol JUGADOR. */
    @PostMapping("/registro")
    public ResponseEntity<MeResponseDTO> registro(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody RegistroRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registrar(jwt.getSubject(), jwt.getClaimAsString("email"), req));
    }

    /**
     * Spec 0023: agregar el documento después del registro. 200 si quedó guardado; 202 si era de una
     * persona sin cuenta y se abrió una solicitud de vínculo (la aprueba su delegado o un admin).
     */
    @PutMapping("/me/documento")
    public ResponseEntity<MeResponseDTO> asignarDocumento(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody DocumentoRequestDTO req) {
        var resultado = authService.asignarDocumento(jwt.getSubject(), req);
        return ResponseEntity.status(resultado.vinculoAbierto() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(resultado.me());
    }

    @PostMapping("/solicitar-rol")
    public ResponseEntity<SolicitudRolResponseDTO> solicitarRol(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody SolicitarRolRequestDTO req) {
        return ResponseEntity.ok(authService.solicitarRol(jwt.getSubject(), req));
    }

    @PostMapping("/invitaciones/{token}/aceptar")
    public ResponseEntity<MeResponseDTO> aceptarInvitacion(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable String token) {
        return ResponseEntity.ok(authService.aceptarInvitacion(jwt.getSubject(), jwt.getClaimAsString("email"), token));
    }
}

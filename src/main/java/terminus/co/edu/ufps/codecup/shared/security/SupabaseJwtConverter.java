package terminus.co.edu.ufps.codecup.shared.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import terminus.co.edu.ufps.codecup.identidad.api.ConsultaCuentas;
import terminus.co.edu.ufps.codecup.identidad.api.CuentaAutenticada;

/**
 * Convierte el access token de Supabase (ya validado con el JWKS) en la
 * autenticación de CodeCup.
 *
 * <p>Los roles salen de {@code identidad.cuenta_roles}, nunca de claims del token.
 * Al {@link Jwt} que reciben los controllers se le agregan {@code cuenta_id},
 * {@code persona_id} y {@code nombre}; los controllers los leen con {@link JwtClaims}.
 * Desde la spec 0022 ya no se agrega la cédula: la competición usa {@code persona_id}.
 */
@Component
@RequiredArgsConstructor
public class SupabaseJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CLAIM_CUENTA_ID = "cuenta_id";
    public static final String CLAIM_PERSONA_ID = "persona_id";
    public static final String CLAIM_NOMBRE = "nombre";

    /**
     * Ningún claim del token puede hacerse pasar por los que agrega el backend. "cedula" se
     * sigue quitando aunque ya nadie lo lea: así un token no puede reintroducirlo.
     */
    private static final Set<String> CLAIMS_RESERVADOS =
            Set.of(CLAIM_CUENTA_ID, CLAIM_PERSONA_ID, CLAIM_NOMBRE, "cedula", "roles");

    private final ConsultaCuentas consultaCuentas;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var cuenta = consultaCuentas.porSubDeSupabase(jwt.getSubject());
        if (cuenta.isPresent() && cuenta.get().bloqueada()) {
            throw new CuentaBloqueadaException();
        }
        List<GrantedAuthority> authorities = cuenta.map(CuentaAutenticada::roles).orElse(List.of()).stream()
                .<GrantedAuthority>map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
        return new JwtAuthenticationToken(enriquecer(jwt, cuenta.orElse(null)), authorities, jwt.getSubject());
    }

    private static Jwt enriquecer(Jwt jwt, CuentaAutenticada cuenta) {
        Map<String, Object> claims = new HashMap<>(jwt.getClaims());
        claims.keySet().removeAll(CLAIMS_RESERVADOS);
        if (cuenta != null) {
            claims.put(CLAIM_CUENTA_ID, cuenta.cuentaId().toString());
            if (cuenta.personaId() != null) claims.put(CLAIM_PERSONA_ID, cuenta.personaId().toString());
            if (cuenta.nombre() != null) claims.put(CLAIM_NOMBRE, cuenta.nombre());
        }
        return Jwt.withTokenValue(jwt.getTokenValue())
                .headers(h -> h.putAll(jwt.getHeaders()))
                .claims(c -> c.putAll(claims))
                .build();
    }
}

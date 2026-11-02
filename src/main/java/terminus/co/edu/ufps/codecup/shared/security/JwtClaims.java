package terminus.co.edu.ufps.codecup.shared.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/** Lectura de los claims que agrega {@link SupabaseJwtConverter}. */
public final class JwtClaims {

    private JwtClaims() {
    }

    /** Id de la cuenta de CodeCup de quien llama; 404 CUENTA_NO_REGISTRADA si aún no se registró. */
    public static UUID cuentaId(Jwt jwt) {
        String valor = jwt.getClaimAsString(SupabaseJwtConverter.CLAIM_CUENTA_ID);
        if (valor == null) {
            throw ApiException.notFound("CUENTA_NO_REGISTRADA", "Completa tu registro en CodeCup para continuar.");
        }
        return UUID.fromString(valor);
    }
}

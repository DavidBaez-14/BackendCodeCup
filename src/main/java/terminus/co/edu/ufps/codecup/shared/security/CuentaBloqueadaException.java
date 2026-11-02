package terminus.co.edu.ufps.codecup.shared.security;

import org.springframework.security.authentication.LockedException;

/** El token es válido, pero un admin bloqueó la cuenta. Responde 403 CUENTA_BLOQUEADA en cualquier endpoint protegido. */
public class CuentaBloqueadaException extends LockedException {

    public CuentaBloqueadaException() {
        super("Tu cuenta está bloqueada. Comunícate con un administrador.");
    }
}

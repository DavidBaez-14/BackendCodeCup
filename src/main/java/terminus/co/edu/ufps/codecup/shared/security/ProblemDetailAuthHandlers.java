package terminus.co.edu.ufps.codecup.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import terminus.co.edu.ufps.codecup.shared.error.GlobalExceptionHandler;

/**
 * Respuestas 401/403 que se deciden en los filtros de seguridad, antes de llegar
 * a un controller. Usan el mismo formato que {@link GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        if (ex instanceof CuentaBloqueadaException) {
            write(request, response, HttpStatus.FORBIDDEN, "CUENTA_BLOQUEADA", ex.getMessage());
            return;
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Inicia sesión para continuar.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permisos para esta acción.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String code, String mensaje) throws IOException {
        ProblemDetail pd = GlobalExceptionHandler.problem(status, code, mensaje);
        pd.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}

package terminus.co.edu.ufps.codecup.shared.error;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Un solo manejador para todo el backend. Responde siempre un {@link ProblemDetail}
 * con dos propiedades extra:
 * <ul>
 *   <li>{@code code}: estable, para que web y móvil decidan sin parsear textos;</li>
 *   <li>{@code mensaje}: el texto para mostrar. Lo lee {@code src/api/http.js} de la web.</li>
 * </ul>
 * Los errores estándar de Spring MVC (404 de ruta, 405, JSON ilegible…) los
 * resuelve {@link ResponseEntityExceptionHandler} con su status correcto.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage());
    }

    /** Antes caía en el manejador de RuntimeException y salía como 400. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permisos para esta acción.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Inicia sesión para continuar.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICTO_DATOS",
                "La operación choca con datos que ya existen. Recarga e inténtalo de nuevo.");
    }

    /**
     * Reglas de negocio heredadas de supercopa: los servicios lanzan
     * RuntimeException con el mensaje para el usuario. Se conserva el 400.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntime(RuntimeException ex) {
        return respond(HttpStatus.BAD_REQUEST, "REGLA_NEGOCIO", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && pd.getProperties() == null) {
            pd.setProperty("code", codigoPorStatus(statusCode));
            pd.setProperty("mensaje", pd.getDetail());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    public static ProblemDetail problem(HttpStatusCode status, String code, String mensaje) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, mensaje);
        pd.setProperty("code", code);
        pd.setProperty("mensaje", mensaje);
        return pd;
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String mensaje) {
        return ResponseEntity.status(status).body(problem(status, code, mensaje));
    }

    private static String codigoPorStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "SOLICITUD_INVALIDA";
            case 404 -> "NO_ENCONTRADO";
            case 405 -> "METODO_NO_PERMITIDO";
            case 415 -> "TIPO_NO_SOPORTADO";
            default -> "ERROR_HTTP_" + status.value();
        };
    }
}

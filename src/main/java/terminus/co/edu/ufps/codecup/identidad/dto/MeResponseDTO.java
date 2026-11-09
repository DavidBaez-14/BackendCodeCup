package terminus.co.edu.ufps.codecup.identidad.dto;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeResponseDTO {

    private UUID cuentaId;

    private UUID personaId;

    private String nombres;

    private String apellidos;

    /** Nombres + apellidos, listo para mostrar. */
    private String nombre;

    private String correo;

    private String tipoDocumento;

    private String numeroDocumento;


    private String estado;

    /** Roles aprobados (ADMINISTRADOR, ARBITRO, DELEGADO, JUGADOR). */
    private List<String> roles;

    private List<String> rolesPendientes;

    /** Solicitud de vínculo con una persona con historial, si la cuenta tiene una pendiente (spec 0023). */
    private VinculoPendiente vinculoPendiente;

    /**
     * Spec 0026: "Tus secciones" (Creador y Árbitro) con lo urgente de cada una. Una sección en null
     * es una que la cuenta no tiene. Los permisos reales son por torneo; {@link #roles} queda por
     * compatibilidad con la web.
     */
    private terminus.co.edu.ufps.codecup.identidad.api.Secciones secciones;

    public record VinculoPendiente(UUID id, UUID personaId, String estado, java.time.LocalDateTime creadoEn) {
    }
}

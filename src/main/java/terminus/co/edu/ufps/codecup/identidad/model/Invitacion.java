package terminus.co.edu.ufps.codecup.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Reemplaza a "crear cuenta desde el admin": el backend no crea usuarios en el
 * proveedor de auth. La persona invitada se registra sola y, si lo hace con el
 * mismo correo, recibe el rol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invitaciones", schema = "identidad")
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /**
     * Siempre en minúsculas. Null solo en una invitación con ámbito "por enlace" (spec 0024):
     * la acepta cualquier cuenta que tenga el enlace, una sola vez.
     */
    @Column(length = 150)
    private String correo;

    /**
     * Con ámbito TORNEO, ADMINISTRADOR significa co-admin de ese torneo (y ARBITRO, árbitro de ese
     * torneo). Sin ámbito, es el rol global.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    /** Null = invitación a la plataforma (rol global). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AmbitoInvitacion ambito;

    @Column(name = "ambito_id")
    private java.util.UUID ambitoId;

    /** Lo que ve el invitado antes de aceptar, por ejemplo el nombre del torneo. */
    @Column(name = "ambito_descripcion", length = 200)
    private String ambitoDescripcion;

    /** Spec 0026: lo que el ámbito necesita además del rol; en un cupo de delegado, el nombre del equipo. */
    @Column(name = "dato_extra", length = 150)
    private String datoExtra;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoInvitacion estado;

    /** Null cuando la crea el sistema (BootstrapAdmin). */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creada_por")
    private Cuenta creadaPor;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aceptada_por")
    private Cuenta aceptadaPor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
}

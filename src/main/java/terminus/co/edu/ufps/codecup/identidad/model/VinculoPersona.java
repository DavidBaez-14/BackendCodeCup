package terminus.co.edu.ufps.codecup.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de una cuenta para quedarse con una persona que ya tiene historial (spec 0023).
 *
 * <p>Pasa cuando alguien se registra con el documento de un miembro que un delegado agregó
 * sin cuenta. Que el documento coincida solo sugiere el vínculo: lo aprueba el delegado de
 * un equipo donde esa persona estuvo, o un admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vinculos_persona", schema = "identidad")
public class VinculoPersona {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** La cuenta que pide el vínculo. */
    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    /** La persona con historial que la cuenta reclama. */
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVinculo estado = EstadoVinculo.PENDIENTE;

    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "resuelto_por_cuenta_id")
    private UUID resueltoPorCuentaId;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;
}

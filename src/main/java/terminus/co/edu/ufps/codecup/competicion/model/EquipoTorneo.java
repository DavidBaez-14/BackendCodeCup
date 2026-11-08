package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "equipo_torneo", schema = "competicion",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"torneo_id", "equipo_id"}),
                @UniqueConstraint(columnNames = {"torneo_id", "delegado_persona_id"})
        })
public class EquipoTorneo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id", nullable = false)
    private Equipo equipo;

    @Column(name = "delegado_persona_id", nullable = false)
    private UUID delegadoPersonaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inscripcion", nullable = false, length = 20)
    private EstadoInscripcion estadoInscripcion;

    @CreationTimestamp
    @Column(name = "fecha_inscripcion", nullable = false, updatable = false)
    private LocalDateTime fechaInscripcion;

    /** Cuenta del admin que aprobó (spec 0022); null si la aprobó el seed. */
    @Column(name = "aprobado_por_cuenta_id")
    private UUID aprobadoPorCuentaId;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "expulsado_por_cuenta_id")
    private UUID expulsadoPorCuentaId;

    @Column(name = "fecha_expulsion")
    private LocalDateTime fechaExpulsion;

    @Column(name = "motivo_expulsion", length = 500)
    private String motivoExpulsion;
}

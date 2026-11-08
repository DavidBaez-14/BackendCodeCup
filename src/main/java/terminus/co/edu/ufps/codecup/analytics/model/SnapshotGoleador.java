package terminus.co.edu.ufps.codecup.analytics.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "snapshot_goleador", schema = "analytics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"torneo_id", "persona_id"}))
public class SnapshotGoleador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "jugador_nombre", length = 150)
    private String jugadorNombre;

    @Column(name = "equipo_nombre", length = 150)
    private String equipoNombre;

    @Column(nullable = false)
    private Integer goles;

    @Column
    private Integer posicion;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}

package terminus.co.edu.ufps.codecup.analytics.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "snapshot_portero", schema = "analytics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"torneo_id", "equipo_torneo_id"}))
public class SnapshotPortero {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(name = "equipo_torneo_id", nullable = false)
    private UUID equipoTorneoId;

    @Column(name = "equipo_nombre", length = 150)
    private String equipoNombre;

    @Column(name = "goles_en_contra", nullable = false)
    private Integer golesEnContra;

    @Column(name = "partidos_jugados", nullable = false)
    private Integer partidosJugados;

    @Column(name = "promedio_gc", precision = 5, scale = 2)
    private BigDecimal promedioGc;

    @Column
    private Integer posicion;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}

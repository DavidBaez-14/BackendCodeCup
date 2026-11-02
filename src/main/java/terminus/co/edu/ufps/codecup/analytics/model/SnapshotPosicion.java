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
@Table(name = "snapshot_posicion", schema = "analytics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"torneo_id", "equipo_torneo_id"}))
public class SnapshotPosicion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(name = "grupo", nullable = false, length = 10)
    private String grupo;

    @Column(name = "equipo_torneo_id", nullable = false)
    private UUID equipoTorneoId;

    @Column(name = "equipo_nombre", nullable = false, length = 150)
    private String equipoNombre;

    @Column(nullable = false)
    private Integer posicion;

    @Column(nullable = false) private Integer pts;
    @Column(nullable = false) private Integer pj;
    @Column(nullable = false) private Integer pg;
    @Column(nullable = false) private Integer pe;
    @Column(nullable = false) private Integer pp;
    @Column(nullable = false) private Integer gf;
    @Column(nullable = false) private Integer gc;
    @Column(nullable = false) private Integer dg;
    @Column(nullable = false) private Integer rojas;

    @Column(length = 20)
    private String form;

    @Builder.Default
    @Column(nullable = false)
    private Boolean descalificado = false;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}

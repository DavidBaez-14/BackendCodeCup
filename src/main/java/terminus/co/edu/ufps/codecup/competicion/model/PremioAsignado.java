package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "premio_asignado", schema = "competicion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"torneo_id", "premio_id"}))
public class PremioAsignado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(name = "premio_id", nullable = false)
    private UUID premioId;

    @Column(length = 20)
    private String cedula;

    @Column(name = "equipo_torneo_id")
    private UUID equipoTorneoId;

    @Builder.Default
    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDateTime fechaAsignacion = LocalDateTime.now();
}

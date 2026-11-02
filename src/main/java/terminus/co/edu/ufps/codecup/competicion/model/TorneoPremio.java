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
@Table(name = "torneo_premio", schema = "competicion")
public class TorneoPremio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Column(name = "premio_id", nullable = false)
    private UUID premioId;

    @Column(length = 150)
    private String titulo;

    @Column(length = 300)
    private String descripcion;

    private Integer monto;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

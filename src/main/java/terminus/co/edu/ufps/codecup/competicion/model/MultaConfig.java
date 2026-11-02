package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "multa_config", schema = "competicion")
public class MultaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "torneo_id", nullable = false, unique = true)
    private UUID torneoId;

    @Column(name = "monto_amarilla", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoAmarilla;

    @Column(name = "monto_azul", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoAzul;

    @Column(name = "monto_roja", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoRoja;

    @Builder.Default
    @Column(name = "fechas_suspension_roja", nullable = false)
    private Integer fechasSuspensionRoja = 1;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

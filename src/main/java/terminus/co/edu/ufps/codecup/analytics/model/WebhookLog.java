package terminus.co.edu.ufps.codecup.analytics.model;

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
@Table(name = "webhook_log", schema = "analytics")
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "torneo_id")
    private UUID torneoId;

    @Column(name = "partido_id")
    private UUID partidoId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @CreationTimestamp
    @Column(name = "recibido_en", nullable = false, updatable = false)
    private LocalDateTime recibidoEn;

    @Column(name = "procesado_en")
    private LocalDateTime procesadoEn;

    @Column(columnDefinition = "TEXT")
    private String error;
}

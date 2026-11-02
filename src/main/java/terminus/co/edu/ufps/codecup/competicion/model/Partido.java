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
@Table(name = "partidos", schema = "competicion")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_local_torneo_id")
    private EquipoTorneo equipoLocalTorneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_visitante_torneo_id")
    private EquipoTorneo equipoVisitanteTorneo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPartido estado = EstadoPartido.PROGRAMADO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fase", length = 20)
    private FaseTorneo fase;

    @Column(name = "jornada")
    private Integer jornada;

    @Column(name = "grupo", length = 1)
    private String grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "siguiente_partido_id")
    private Partido siguientePartido;

    @Enumerated(EnumType.STRING)
    @Column(name = "siguiente_slot", length = 10)
    private SlotPartido siguienteSlot;

    @Column(name = "tipo_cierre", length = 30)
    private String tipoCierre;
}

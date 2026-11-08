package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "partido_jugador", schema = "competicion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"partido_id", "persona_id"}))
public class PartidoJugador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_id", nullable = false)
    private Partido partido;

    /** Persona que juega (spec 0022). Antes era una relación con competicion.jugadores por cédula. */
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_torneo_id", nullable = false)
    private EquipoTorneo equipoTorneo;

    @Builder.Default
    @Column(nullable = false)
    private Integer goles = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean jugo = true;
}

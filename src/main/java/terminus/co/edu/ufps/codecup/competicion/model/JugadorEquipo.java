package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jugador_equipo", schema = "competicion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cedula", "torneo_id"}))
public class JugadorEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String cedula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_torneo_id", nullable = false)
    private EquipoTorneo equipoTorneo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMembresia estado = EstadoMembresia.ACTIVO;

    @Column(name = "numero_camiseta")
    private Integer numeroCamiseta;

    // ─── Snapshot al momento de inscripción ───────────────────────
    // Registro histórico del torneo (ej. "Estudiantes por semestre" de un
    // torneo pasado debe reflejar el semestre que tenían entonces). El nombre
    // es el de la persona registrada o el que escribió el delegado si no tiene
    // cuenta (spec 0018). Semestre y rol los llenará el CSV de informes (M5).
    @Column(name = "nombre_snapshot", length = 150)
    private String nombreSnapshot;

    @Column(name = "semestre_snapshot")
    private Integer semestreSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_jugador_snapshot", length = 20)
    private RolJugador rolJugadorSnapshot;

    // Snapshot deportivo al momento de inscripcion. Permite que estadisticas
    // historicas (ej. "valla menos vencida") respeten la posicion que el
    // jugador tenia entonces, aunque hoy haya editado su perfil.
    @Column(name = "altura_cm_snapshot")
    private Integer alturaCmSnapshot;

    @Column(name = "pierna_habil_snapshot", length = 20)
    private String piernaHabilSnapshot;

    @Column(name = "posicion_snapshot", length = 30)
    private String posicionSnapshot;

    @Column(name = "snapshot_at")
    private LocalDateTime snapshotAt;
}

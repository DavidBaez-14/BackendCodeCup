package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Solicitud de un jugador para ingresar al plantel de un equipo en un torneo.
 *
 * Nombre y correo se copian de la persona registrada al crear (la tabla los
 * exige NOT NULL). Quien pide el ingreso siempre tiene cuenta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "solicitudes_equipo", schema = "competicion")
public class SolicitudEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_torneo_id", nullable = false)
    private EquipoTorneo equipoTorneo;

    @Column(nullable = false, length = 20)
    private String cedula;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 150)
    private String correo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Builder.Default
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;
}

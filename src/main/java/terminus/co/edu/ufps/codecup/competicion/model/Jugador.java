package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Perfil deportivo + cosmético del jugador en MS2.
 *
 * Los datos académicos oficiales (rol académico, código universitario,
 * semestre) viven en MS1 (padrón) y se snapshotean en {@link JugadorEquipo}
 * al momento de inscripción para reportes históricos.
 *
 * Nombre y correo se copian del padrón MS1 al crear la fila local porque la
 * tabla los exige NOT NULL — sirven como display si MS1 no responde, pero la
 * fuente de verdad sigue siendo MS1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jugadores", schema = "competicion")
public class Jugador {

    @Id
    @Column(length = 20)
    private String cedula;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 150)
    private String correo;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "pierna_habil", length = 20)
    private String piernaHabil;

    @Column(length = 30)
    private String posicion;

    @Column(length = 60)
    private String apodo;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

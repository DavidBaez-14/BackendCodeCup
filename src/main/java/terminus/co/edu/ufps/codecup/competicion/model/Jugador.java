package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Perfil deportivo + cosmético del jugador en MS2.
 *
 * La llave sigue siendo el número de documento (normalizado) hasta M2.
 * El jugador puede no tener cuenta: el delegado lo agrega con su nombre
 * (spec 0018). Si se registra después, hereda esta fila y su historial.
 *
 * Nombre y correo se copian al crear la fila (la tabla exige el nombre NOT
 * NULL): de la persona registrada o, si no tiene cuenta, el nombre que
 * escribió el delegado. Para mostrar, gana el nombre de la persona registrada.
 * Los datos académicos (semestre, rol) ya no vienen de un padrón: los llenará
 * el CSV opcional de informes (M5) en los snapshots de {@link JugadorEquipo}.
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

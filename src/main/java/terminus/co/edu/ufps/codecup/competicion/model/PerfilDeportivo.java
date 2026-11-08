package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Datos deportivos y cosméticos de una persona (spec 0022; antes competicion.jugadores).
 *
 * <p>La llave es {@code identidad.personas.id}. El nombre y el correo no viven aquí: los
 * tiene identidad. Es opcional (spec 0020): quien no lo completó no tiene fila, y se
 * crea la primera vez que guarda algo en "Mi perfil".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "perfil_deportivo", schema = "competicion")
public class PerfilDeportivo {

    @Id
    @Column(name = "persona_id")
    private UUID personaId;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "pierna_habil", length = 20)
    private String piernaHabil;

    @Column(length = 30)
    private String posicion;

    /** Cosmético y opcional: si existe, se muestra antes que el nombre. */
    @Column(length = 60)
    private String apodo;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Apodo si lo tiene; si no, null. */
    public String apodoVisible() {
        return apodo == null || apodo.isBlank() ? null : apodo;
    }
}

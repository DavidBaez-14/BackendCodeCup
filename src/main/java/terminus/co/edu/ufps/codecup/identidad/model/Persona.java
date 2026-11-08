package terminus.co.edu.ufps.codecup.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * La persona del mundo real, separada de la cuenta con la que inicia sesión.
 * Es la llave de la competición (spec 0022): plantel, partidos, multas… apuntan a su id.
 * Puede no tener cuenta: un miembro que el delegado agregó y que aún no se registró.
 * El documento es opcional en el modelo; el número no se repite aunque cambie el tipo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "personas", schema = "identidad",
        uniqueConstraints = @UniqueConstraint(name = "personas_documento_uk",
                columnNames = {"tipo_documento", "numero_documento"}))
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombres;

    @Column(length = 100)
    private String apellidos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 20)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", length = 20)
    private String numeroDocumento;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String nombreCompleto() {
        return apellidos == null || apellidos.isBlank() ? nombres : nombres + " " + apellidos;
    }
}

package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catálogo de deportes (spec 0029). Lo carga Flyway (V10); el código es la llave. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deportes", schema = "competicion")
public class Deporte {

    public static final String FUTSAL = "FUTSAL";

    @Id
    @Column(length = 30)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_competencia", nullable = false, length = 20)
    private TipoCompetencia tipoCompetencia;

    @Column(nullable = false)
    private Integer orden;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    /** EQUIPO: se inscriben equipos. INDIVIDUAL: cada persona se inscribe sola. */
    public enum TipoCompetencia {
        EQUIPO,
        INDIVIDUAL
    }
}

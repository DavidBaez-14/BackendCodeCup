package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Un árbitro asignado a un partido (spec 0025). Un partido puede tener varios. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "partido_arbitros", schema = "competicion",
        uniqueConstraints = @UniqueConstraint(name = "partido_arbitros_partido_cuenta_uk",
                columnNames = {"partido_id", "cuenta_id"}))
public class PartidoArbitro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_id", nullable = false)
    private Partido partido;

    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    @Column(name = "asignado_en", nullable = false)
    private LocalDateTime asignadoEn;

    @Column(name = "asignado_por_cuenta_id")
    private UUID asignadoPorCuentaId;
}

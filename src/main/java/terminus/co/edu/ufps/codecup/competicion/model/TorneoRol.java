package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Permiso de una cuenta sobre un torneo (spec 0024). La cuenta se guarda por id, sin relación
 * JPA: es de otro módulo (identidad) y se consulta por {@code identidad.api}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "torneo_roles", schema = "competicion",
        uniqueConstraints = @UniqueConstraint(name = "torneo_roles_torneo_cuenta_rol_uk",
                columnNames = {"torneo_id", "cuenta_id", "rol"}))
public class TorneoRol {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolTorneo rol;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    /** Quien lo dio: el creador del torneo o el admin que invitó. Null en la migración o el seed. */
    @Column(name = "creado_por_cuenta_id")
    private UUID creadoPorCuentaId;
}

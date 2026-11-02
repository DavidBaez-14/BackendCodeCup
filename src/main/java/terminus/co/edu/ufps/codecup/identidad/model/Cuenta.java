package terminus.co.edu.ufps.codecup.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
 * Cuenta de inicio de sesión. Su id es propio: el usuario del proveedor de auth
 * se guarda aparte en (proveedor, proveedor_sub), así cambiar de proveedor no
 * obliga a migrar ninguna FK.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cuentas", schema = "identidad",
        uniqueConstraints = @UniqueConstraint(name = "cuentas_proveedor_sub_uk",
                columnNames = {"proveedor", "proveedor_sub"}))
public class Cuenta {

    public static final String PROVEEDOR_SUPABASE = "SUPABASE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String proveedor;

    @Column(name = "proveedor_sub", nullable = false, length = 100)
    private String proveedorSub;

    /** Siempre en minúsculas. */
    @Column(length = 150)
    private String correo;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", unique = true)
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}

package terminus.co.edu.ufps.codecup.competicion.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "torneos", schema = "competicion")
public class Torneo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private Integer edicion;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTorneo estado = EstadoTorneo.BORRADOR;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato", length = 30)
    private FormatoTorneo formato;

    @Column(name = "num_grupos")
    private Integer numGrupos;

    @Column(name = "clasifican_por_grupo")
    private Integer clasificanPorGrupo;

    @Builder.Default
    @Column(name = "repechaje", nullable = false)
    private Boolean repechaje = false;

    @Column(name = "rondas_playoff", length = 200)
    private String rondasPlayoff;

    @Column(name = "configurado_en")
    private LocalDateTime configuradoEn;

    @Column(name = "monto_inscripcion", precision = 12, scale = 2)
    private BigDecimal montoInscripcion;

    @Column(name = "cuenta_destino", length = 120)
    private String cuentaDestino;

    @Column(name = "banco_destino", length = 80)
    private String bancoDestino;

    @Column(name = "titular_cuenta", length = 150)
    private String titularCuenta;
}

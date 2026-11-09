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

    /** Spec 0023: si el admin lo activa, todo miembro del torneo debe tener documento. Por defecto no. */
    @Builder.Default
    @Column(name = "exige_documento", nullable = false)
    private Boolean exigeDocumento = false;

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

    // ─── Spec 0029: deporte, visibilidad y cupos ──────────────────

    /** Código del catálogo {@code competicion.deportes}. */
    @Builder.Default
    @Column(name = "deporte", nullable = false, length = 30)
    private String deporte = Deporte.FUTSAL;

    /** Por defecto PUBLICO, como se comportaban los torneos antes de la 0029. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibilidad", nullable = false, length = 20)
    private Visibilidad visibilidad = Visibilidad.PUBLICO;

    /** Para el enlace de compartir; se regenera para revocar un enlace filtrado. */
    @Column(name = "enlace_token", nullable = false, unique = true, length = 64)
    private String enlaceToken;

    /** Máximo de inscripciones aprobadas; null = sin límite. */
    @Column(name = "cupos")
    private Integer cupos;

    /** Último día para inscribirse; null = hasta que el organizador las cierre. */
    @Column(name = "cierre_inscripciones")
    private LocalDate cierreInscripciones;

    @Column(name = "reglas", length = 2000)
    private String reglas;

    @jakarta.persistence.PrePersist
    void asegurarEnlace() {
        if (enlaceToken == null) {
            enlaceToken = nuevoEnlaceToken();
        }
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 16 bytes al azar en base64url (22 caracteres). */
    public static String nuevoEnlaceToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

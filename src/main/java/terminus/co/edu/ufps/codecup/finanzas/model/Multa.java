package terminus.co.edu.ufps.codecup.finanzas.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Multa generada automáticamente al cerrar un partido con tarjetas (HU29),
 * o por acumulación de amarillas. Se asocia siempre a un jugador (cédula),
 * a un partido (origen) y a un equipo_torneo (contexto del torneo).
 *
 * Mientras la multa esté en estado distinto de PAGADA o CONDONADA, el
 * jugador está SUSPENDIDO y MS2 lo excluye de la lista de elegibles (HU19).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "multas", schema = "finanzas")
public class Multa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String cedula;

    @Column(name = "partido_id", nullable = false)
    private UUID partidoId;

    @Column(name = "equipo_torneo_id", nullable = false)
    private UUID equipoTorneoId;

    @Column(name = "torneo_id", nullable = false)
    private UUID torneoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sancion", nullable = false, length = 30)
    private TipoSancion tipoSancion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMulta estado = EstadoMulta.PENDIENTE;

    /**
     * Partidos de suspensión adicionales generados por esta sanción
     * (ej. tarjeta roja directa = 2 partidos). Se evalúan al chequear
     * elegibilidad del jugador para los próximos partidos.
     */
    @Builder.Default
    @Column(name = "partidos_suspension", nullable = false)
    private Integer partidosSuspension = 0;

    @Builder.Default
    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion = LocalDateTime.now();

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "partidos_suspension_restantes")
    private Integer partidosSuspensionRestantes;

    @Column(name = "pagado_por_cedula", length = 20)
    private String pagadoPorCedula;

    @Column(name = "partido_pago_id")
    private UUID partidoPagoId;

    @Builder.Default
    @Column(name = "habilitado_manual", nullable = false)
    private Boolean habilitadoManual = false;

    @Column(name = "habilitado_por_cedula", length = 20)
    private String habilitadoPorCedula;

    @Column(name = "habilitado_en")
    private LocalDateTime habilitadoEn;

    @Column(name = "motivo_habilitacion", length = 200)
    private String motivoHabilitacion;
}

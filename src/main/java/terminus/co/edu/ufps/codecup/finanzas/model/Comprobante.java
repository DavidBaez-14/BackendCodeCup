package terminus.co.edu.ufps.codecup.finanzas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Comprobante de pago subido por un delegado (HU25 inscripción, HU27 multa).
 *
 * El archivo en sí se almacena fuera de BD (Supabase Storage) y aquí
 * solo guardamos la URL pública/firmada. El admin lo revisa y aprueba
 * o rechaza con motivo (HU28).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comprobantes", schema = "finanzas")
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoComprobante tipo;

    /**
     * Identificador del objeto al que aplica el comprobante:
     * - Si tipo = INSCRIPCION → equipo_torneo_id
     * - Si tipo = MULTA       → multa_id
     */
    @Column(name = "referencia_id", nullable = false)
    private UUID referenciaId;

    @Column(name = "url_archivo", nullable = false, length = 500)
    private String urlArchivo;

    @Column(name = "cedula_uploader", nullable = false, length = 20)
    private String cedulaUploader;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoComprobante estado = EstadoComprobante.PENDIENTE_REVISION;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "aprobado_por", length = 20)
    private String aprobadoPor;

    @Builder.Default
    @Column(name = "fecha_subida", nullable = false)
    private LocalDateTime fechaSubida = LocalDateTime.now();

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
}

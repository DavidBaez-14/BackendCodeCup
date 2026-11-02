package terminus.co.edu.ufps.codecup.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.analytics.dto.WebhookPayload;
import terminus.co.edu.ufps.codecup.analytics.model.WebhookLog;
import terminus.co.edu.ufps.codecup.analytics.repository.WebhookLogRepository;
import terminus.co.edu.ufps.codecup.analytics.service.SnapshotBuilderService;
import terminus.co.edu.ufps.codecup.analytics.service.TorneoCerradoService;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Disparadores de reconstrucción de snapshots.
 *
 * <p><b>Estado:</b> estos tres endpoints existen desde que MS4 era un binario aparte,
 * pero <b>nadie los llama</b>: MS2 nunca implementó la publicación del evento de cierre.
 * Se conservan tal cual durante la fusión (spec 0015) para no eliminar superficie de API.
 * Sustituirlos por un {@code ApplicationEvent} en proceso disparado desde el cierre de
 * partido/torneo de MS2 es trabajo de la spec 0016.
 *
 * <p>La protección sigue siendo el shared secret {@code X-Webhook-Secret}, verificado
 * dentro del método: la cadena de seguridad de MS2 deja pasar estas rutas sin JWT.
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class WebhookController {

    private final SnapshotBuilderService snapshotBuilder;
    private final TorneoCerradoService torneoCerradoService;
    private final WebhookLogRepository webhookLogRepo;
    private final ObjectMapper objectMapper;

    @Value("${analytics.webhook.secret}")
    private String expectedSecret;

    @PostMapping("/webhooks/partido-cerrado")
    public ResponseEntity<Void> partidoCerrado(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody WebhookPayload payload) {
        if (secretInvalido(secret)) {
            log.warn("Webhook recibido con secret inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (payload == null || payload.getTorneoId() == null) {
            return ResponseEntity.badRequest().build();
        }
        WebhookLog logEntry = registrar("partido-cerrado", payload);

        // Reconstrucción asincrónica: respondemos 202 inmediatamente.
        snapshotBuilder.reconstruirAsync(payload.getTorneoId());

        logEntry.setProcesadoEn(LocalDateTime.now());
        webhookLogRepo.save(logEntry);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/webhooks/torneo-cerrado")
    public ResponseEntity<Void> torneoCerrado(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody WebhookPayload payload) {
        if (secretInvalido(secret)) {
            log.warn("Webhook torneo-cerrado recibido con secret inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (payload == null || payload.getTorneoId() == null) {
            return ResponseEntity.badRequest().build();
        }
        WebhookLog logEntry = registrar("torneo-cerrado", payload);

        torneoCerradoService.procesarCierre(payload.getTorneoId());

        logEntry.setProcesadoEn(LocalDateTime.now());
        webhookLogRepo.save(logEntry);
        return ResponseEntity.accepted().build();
    }

    /**
     * Endpoint debug: fuerza recompute manual. Útil cuando los snapshots quedaron atrás
     * y queremos resincronizar sin esperar el próximo cierre.
     */
    @PostMapping("/torneos/{torneoId}/recompute")
    public ResponseEntity<Void> recompute(
            @PathVariable UUID torneoId,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        if (secretInvalido(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        snapshotBuilder.reconstruirAsync(torneoId);
        return ResponseEntity.accepted().build();
    }

    private boolean secretInvalido(String secret) {
        // Sin secret configurado los webhooks quedan deshabilitados: "" nunca debe validar un header vacío.
        return expectedSecret == null || expectedSecret.isBlank() || !expectedSecret.equals(secret);
    }

    private WebhookLog registrar(String tipo, WebhookPayload payload) {
        WebhookLog logEntry = WebhookLog.builder()
                .tipo(tipo)
                .torneoId(payload.getTorneoId())
                .partidoId(payload.getPartidoId())
                .build();
        try {
            logEntry.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ignore) {
            // El log del webhook es auxiliar: si el payload no serializa, no aborta el flujo.
        }
        return webhookLogRepo.save(logEntry);
    }
}

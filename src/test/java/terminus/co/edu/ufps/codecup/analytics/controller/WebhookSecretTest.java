package terminus.co.edu.ufps.codecup.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import terminus.co.edu.ufps.codecup.analytics.service.SnapshotBuilderService;

/**
 * Spec 0017, criterio 19. El repo ya no trae un secret por defecto: si no se
 * configura, un header vacío no debe pasar ("".equals("") era true).
 */
@ExtendWith(MockitoExtension.class)
class WebhookSecretTest {

    @Mock SnapshotBuilderService snapshotBuilder;
    @InjectMocks WebhookController controller;

    @Test
    void secretVacio_rechazaHeaderVacio() {
        ReflectionTestUtils.setField(controller, "expectedSecret", "");

        var resp = controller.recompute(UUID.randomUUID(), "");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(snapshotBuilder);
    }

    @Test
    void secretConfigurado_aceptaElCorrecto() {
        ReflectionTestUtils.setField(controller, "expectedSecret", "s3cr3t");
        var torneoId = UUID.randomUUID();

        assertThat(controller.recompute(torneoId, "otro").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.recompute(torneoId, "s3cr3t").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(snapshotBuilder).reconstruirAsync(torneoId);
    }
}

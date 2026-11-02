package terminus.co.edu.ufps.codecup.finanzas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Spec 0017, criterio 18. La clave secreta nueva (sb_secret_...) no es un JWT:
 * Supabase la rechaza en Authorization: Bearer. Debe ir solo en apikey.
 */
class StorageServiceTest {

    private static final String URL = "https://proyecto.supabase.co";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private StorageService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new StorageService(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "supabaseUrl", URL);
        ReflectionTestUtils.setField(service, "secretKey", "sb_secret_prueba");
        ReflectionTestUtils.setField(service, "bucket", "comprobantes-inscripcion");
    }

    @Test
    void firmarSubida_usaSoloApikey() {
        var etId = UUID.randomUUID();
        server.expect(requestTo(URL + "/storage/v1/object/upload/sign/comprobantes-inscripcion/" + etId + "/comprobante"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "sb_secret_prueba"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"url\":\"/object/upload/sign/x?token=t\"}", MediaType.APPLICATION_JSON));

        var result = service.generarUploadUrl(etId);

        assertThat(result.uploadUrl()).isEqualTo(URL + "/storage/v1/object/upload/sign/x?token=t");
        server.verify();
    }

    @Test
    void archivoExiste_usaSoloApikey() {
        server.expect(requestTo(URL + "/storage/v1/object/info/comprobantes-inscripcion/abc/comprobante"))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header("apikey", "sb_secret_prueba"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess());

        assertThat(service.archivoExiste("abc/comprobante")).isTrue();
        server.verify();
    }

    @Test
    void sinClave_fallaConMensajeClaro() {
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertThatThrownBy(() -> service.generarDownloadUrl("abc/comprobante"))
                .isInstanceOf(StorageService.StorageNoConfiguradoException.class)
                .hasMessageContaining("SUPABASE_SECRET_KEY");
        assertThatThrownBy(() -> service.archivoExiste("abc/comprobante"))
                .isInstanceOf(StorageService.StorageNoConfiguradoException.class);
    }
}

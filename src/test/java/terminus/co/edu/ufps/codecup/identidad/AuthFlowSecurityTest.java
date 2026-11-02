package terminus.co.edu.ufps.codecup.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/** Spec 0017, criterios 2 a 10: login de Supabase validado por el backend, roles desde la BD. */
class AuthFlowSecurityTest extends AuthTestSupport {

    @Test
    void sinToken_401ConCodigo() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void swaggerEsPublicoYSeGeneraSinErrores() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/me']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/invitaciones']").exists());
    }

    @Test
    void me_cuentaNoRegistrada_404() throws Exception {
        var u = usuario();
        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUENTA_NO_REGISTRADA"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void registro_creaPersonaCuentaYJugador() throws Exception {
        var u = usuario();
        registrar(u, "CC", "1.090 555 123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", contains("JUGADOR")))
                .andExpect(jsonPath("$.cedula").value("1090555123"))
                .andExpect(jsonPath("$.nombre").value("Ana María Pérez"))
                .andExpect(jsonPath("$.correo").value(u.correo()));

        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("JUGADOR")));

        var cuenta = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow();
        assertThat(cuenta.getPersona().getNumeroDocumento()).isEqualTo("1090555123");
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        // JUGADOR ya sirve en la siguiente petición, sin emitir tokens.
        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void registro_documentoRepetido_409_aunqueCambieElTipo() throws Exception {
        String doc = registrar(usuario());
        registrar(usuario(), "TI", doc)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REGISTRADO"));
    }

    @Test
    void registro_dosVeces_409() throws Exception {
        var u = usuario();
        registrar(u);
        registrar(u, "CC", documentoAlAzar())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUENTA_YA_REGISTRADA"));
    }

    @Test
    void registro_sinDocumento_400Validacion() throws Exception {
        var u = usuario();
        mvc.perform(post("/api/auth/registro").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombres", "Ana", "apellidos", "Pérez"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    void rolesSalenDeLaBdNoDelToken() throws Exception {
        // Claims inventados: ninguno debe dar permisos ni suplantar la cédula.
        var u = usuario(Map.of(
                "roles", List.of("ADMINISTRADOR"),
                "app_metadata", Map.of("roles", List.of("ADMINISTRADOR")),
                "cedula", "999",
                "cuenta_id", "00000000-0000-0000-0000-000000000000"));
        String doc = registrar(u);

        mvc.perform(get("/api/admin/cuentas").header("Authorization", u.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cedula").value(doc));
    }

    @Test
    void jugadorEnEndpointDeAdminOArbitro_403AccesoDenegado() throws Exception {
        var u = usuario();
        registrar(u);
        mvc.perform(get("/api/admin/roles/pendientes").header("Authorization", u.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
        // Endpoint de árbitro heredado de supercopa: antes respondía 400.
        mvc.perform(get("/api/supercopa/admin/partidos/{id}/eventos", java.util.UUID.randomUUID())
                        .header("Authorization", u.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void cuentaBloqueada_403EnCualquierEndpoint() throws Exception {
        var u = usuario();
        registrar(u);
        var cuenta = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow();
        cuenta.setEstado(EstadoCuenta.BLOQUEADA);
        cuentaRepository.save(cuenta);

        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUENTA_BLOQUEADA"));
        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void solicitarRol_aprobar_daRolSinTokenNuevo() throws Exception {
        var jugador = usuario();
        registrar(jugador);
        mvc.perform(post("/api/auth/solicitar-rol").header("Authorization", jugador.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("rol", "DELEGADO", "motivoSolicitud", "Tengo equipo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(get("/api/supercopa/delegado/torneos/disponibles").header("Authorization", jugador.bearer()))
                .andExpect(status().isForbidden());

        var admin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        String pendientes = mvc.perform(get("/api/admin/roles/pendientes").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String solicitudId = null;
        for (JsonNode n : objectMapper.readTree(pendientes)) {
            if (jugador.correo().equals(n.path("correo").asText())) solicitudId = n.path("id").asText();
        }
        assertThat(solicitudId).as("la solicitud aparece en pendientes").isNotNull();

        mvc.perform(post("/api/admin/roles/{id}/aprobar", solicitudId).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());

        // Mismo token de antes: el rol sale de la BD en cada petición.
        mvc.perform(get("/api/auth/me").header("Authorization", jugador.bearer()))
                .andExpect(jsonPath("$.roles", hasItem("DELEGADO")))
                .andExpect(jsonPath("$.rolesPendientes", not(hasItem("DELEGADO"))));
        mvc.perform(get("/api/supercopa/delegado/torneos/disponibles").header("Authorization", jugador.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void solicitarRol_reglas() throws Exception {
        var u = usuario();
        registrar(u);
        for (String rol : List.of("ADMINISTRADOR", "JUGADOR")) {
            mvc.perform(post("/api/auth/solicitar-rol").header("Authorization", u.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("rol", rol))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROL_NO_SOLICITABLE"));
        }
        mvc.perform(post("/api/auth/solicitar-rol").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("rol", "ARBITRO"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/solicitar-rol").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("rol", "ARBITRO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLICITUD_EXISTENTE"));
    }

    @Test
    void solicitarRol_sinRegistro_404() throws Exception {
        var u = usuario();
        mvc.perform(post("/api/auth/solicitar-rol").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("rol", "DELEGADO"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUENTA_NO_REGISTRADA"));
    }
}

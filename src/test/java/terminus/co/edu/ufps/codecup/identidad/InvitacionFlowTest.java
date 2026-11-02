package terminus.co.edu.ufps.codecup.identidad;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/** Spec 0017, criterios 13 y 14: invitaciones en lugar de "crear cuenta". */
class InvitacionFlowTest extends AuthTestSupport {

    private ResultActions invitar(Usuario admin, String correo, String rol) throws Exception {
        return mvc.perform(post("/api/admin/invitaciones").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("correo", correo, "rol", rol))));
    }

    private JsonNode crearInvitacion(Usuario admin, String correo, String rol) throws Exception {
        String body = invitar(admin, correo, rol).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String correoAlAzar() {
        return "inv" + UUID.randomUUID().toString().substring(0, 8) + "@correo.test";
    }

    @Test
    void invitacion_seAplicaAlRegistrarse_sinDistinguirMayusculas() throws Exception {
        var admin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        String correo = correoAlAzar();
        var inv = crearInvitacion(admin, correo.toUpperCase(), "ARBITRO");
        org.assertj.core.api.Assertions.assertThat(inv.path("enlace").asText())
                .startsWith("http://localhost:5173/invitacion/");
        org.assertj.core.api.Assertions.assertThat(inv.path("estado").asText()).isEqualTo("PENDIENTE");

        var invitado = usuario(UUID.randomUUID().toString(), correo, Map.of());
        registrar(invitado, "CC", documentoAlAzar())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItem("ARBITRO")))
                .andExpect(jsonPath("$.roles", hasItem("JUGADOR")));

        mvc.perform(get("/api/admin/invitaciones").param("estado", "ACEPTADA").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(inv.path("id").asText())));
    }

    @Test
    void revocada_noSeAplica() throws Exception {
        var admin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        String correo = correoAlAzar();
        var inv = crearInvitacion(admin, correo, "DELEGADO");
        mvc.perform(delete("/api/admin/invitaciones/{id}", inv.path("id").asText()).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());

        var invitado = usuario(UUID.randomUUID().toString(), correo, Map.of());
        registrar(invitado, "CC", documentoAlAzar())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", not(hasItem("DELEGADO"))));
    }

    @Test
    void noAdmin_403() throws Exception {
        var jugador = usuario();
        registrar(jugador);
        invitar(jugador, correoAlAzar(), "ARBITRO")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void duplicada_409_yJugadorNoSeInvita() throws Exception {
        var admin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        String correo = correoAlAzar();
        crearInvitacion(admin, correo, "ARBITRO");
        invitar(admin, correo, "ARBITRO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITACION_DUPLICADA"));
        invitar(admin, correoAlAzar(), "JUGADOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROL_NO_INVITABLE"));
    }

    @Test
    void aceptar_conCuentaExistente_mismoCorreo() throws Exception {
        var admin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        var existente = usuario();
        registrar(existente);
        var inv = crearInvitacion(admin, existente.correo(), "DELEGADO");

        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", inv.path("token").asText())
                        .header("Authorization", existente.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("DELEGADO")));
        // Ya usada.
        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", inv.path("token").asText())
                        .header("Authorization", existente.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITACION_NO_VIGENTE"));
    }

    @Test
    void aceptar_otroCorreo_403() throws Exception {
        var admin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        var otro = usuario();
        registrar(otro);
        var inv = crearInvitacion(admin, correoAlAzar(), "ARBITRO");

        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", inv.path("token").asText())
                        .header("Authorization", otro.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVITACION_OTRO_CORREO"));
        mvc.perform(get("/api/auth/me").header("Authorization", otro.bearer()))
                .andExpect(jsonPath("$.roles", not(hasItem("ARBITRO"))));
    }

    @Test
    void aceptar_tokenInexistente_404() throws Exception {
        var u = usuario();
        registrar(u);
        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", "no-existe").header("Authorization", u.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITACION_NO_ENCONTRADA"));
    }
}

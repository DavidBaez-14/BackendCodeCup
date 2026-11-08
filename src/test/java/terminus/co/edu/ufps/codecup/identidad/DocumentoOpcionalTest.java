package terminus.co.edu.ufps.codecup.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/** Spec 0023, criterios 1, 3 y 12: el documento es opcional y se puede agregar después. */
class DocumentoOpcionalTest extends AuthTestSupport {

    @Test
    void registroSinDocumento_201_yLaPersonaQuedaSinDocumento() throws Exception {
        var u = usuario();
        registrarSinDocumento(u)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("JUGADOR"))
                .andExpect(jsonPath("$.numeroDocumento").doesNotExist())
                .andExpect(jsonPath("$.tipoDocumento").doesNotExist())
                .andExpect(jsonPath("$.vinculoPendiente").doesNotExist());

        var persona = personaDe(u);
        assertThat(persona.getNumeroDocumento()).isNull();
        assertThat(persona.getTipoDocumento()).isNull();
    }

    @Test
    void registroConDocumentoIncompleto_400Validacion() throws Exception {
        registrarCon(usuario(), Map.of("tipoDocumento", "CC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
        registrarCon(usuario(), Map.of("numeroDocumento", documentoAlAzar()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    void agregarDocumentoDespues_loGuarda_ySoloUnaVez() throws Exception {
        var u = usuario();
        registrarSinDocumento(u).andExpect(status().isCreated());
        String doc = documentoAlAzar();

        asignarDocumento(u, "CC", doc)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").value(doc))
                .andExpect(jsonPath("$.tipoDocumento").value("CC"));
        assertThat(personaDe(u).getNumeroDocumento()).isEqualTo(doc);

        asignarDocumento(u, "CC", documentoAlAzar())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_YA_ASIGNADO"));
    }

    @Test
    void agregarElDocumentoDeOtraCuenta_409DocumentoRegistrado() throws Exception {
        String ajeno = registrar(usuario());
        var u = usuario();
        registrarSinDocumento(u).andExpect(status().isCreated());

        asignarDocumento(u, "CC", ajeno)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REGISTRADO"));
        assertThat(personaDe(u).getNumeroDocumento()).isNull();
    }

    @Test
    void agregarElDocumentoDeUnMiembroSinCuenta_202_abreVinculo_ySinGuardarlo() throws Exception {
        String doc = documentoAlAzar();
        var miembro = personaRepository.save(Persona.builder()
                .nombres("Pedro Sin Cuenta").tipoDocumento(TipoDocumento.CC).numeroDocumento(doc).build());
        var u = usuario();
        registrarSinDocumento(u).andExpect(status().isCreated());

        asignarDocumento(u, "CC", doc)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.vinculoPendiente.personaId").value(miembro.getId().toString()))
                .andExpect(jsonPath("$.vinculoPendiente.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.numeroDocumento").doesNotExist());
        assertThat(personaDe(u).getNumeroDocumento()).isNull();
    }

    @Test
    void registrarseConElDocumentoDeOtraCuenta_sigue409() throws Exception {
        String ajeno = registrar(usuario());

        registrar(usuario(), "CC", ajeno)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REGISTRADO"));
    }

    // ── Ayudas ──────────────────────────────────────────────────────────

    private ResultActions registrarSinDocumento(Usuario u) throws Exception {
        return registrarCon(u, Map.of());
    }

    private ResultActions registrarCon(Usuario u, Map<String, Object> extra) throws Exception {
        var body = new HashMap<String, Object>(Map.of("nombres", "Lucía", "apellidos", "Gómez"));
        body.putAll(extra);
        return mvc.perform(post("/api/auth/registro")
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions asignarDocumento(Usuario u, String tipo, String numero) throws Exception {
        return mvc.perform(put("/api/auth/me/documento")
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("tipoDocumento", tipo, "numeroDocumento", numero))));
    }

    private Persona personaDe(Usuario u) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona();
    }
}

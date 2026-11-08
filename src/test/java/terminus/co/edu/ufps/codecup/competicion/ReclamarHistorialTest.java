package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0023, D3 (aprobado el 8-nov-2026): alguien a quien el delegado anotó solo con el nombre se
 * reconoce en el plantel ("ese soy yo") y reclama ese historial. Lo aprueba el delegado de un equipo
 * donde estuvo esa persona o un admin.
 */
class ReclamarHistorialTest extends AuthTestSupport {

    private static final String NOMBRE_REGISTRADO = "Ana María Pérez"; // el que usa AuthTestSupport.registrar

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;

    @Test
    void reconocerseEnElPlantel_reclamar_yElDelegadoLoAprueba() throws Exception {
        var m = mundo();
        UUID juan = agregarSoloConNombre(m, "Juan Sin Documento");
        var u = usuario();
        String suDocumento = registrar(u);
        UUID suPersona = personaDe(u).getId();

        // Ve a los miembros sin cuenta del equipo, sin documentos.
        String lista = mvc.perform(get("/api/supercopa/vinculos/equipo-torneo/{id}/miembros-sin-cuenta", m.inscripcion().getId())
                        .header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.personaId == '%s')].nombre", juan).value(hasItem("Juan Sin Documento")))
                .andReturn().getResponse().getContentAsString();
        assertThat(lista).doesNotContain("numeroDocumento").doesNotContain("correo");

        String vinculoId = reclamar(u, juan)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personaId").value(juan.toString()))
                .andExpect(jsonPath("$.solicitanteNombre").value(NOMBRE_REGISTRADO))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(vinculoId).get("id").asText();

        mvc.perform(get("/api/supercopa/vinculos/pendientes").header("Authorization", m.delegado().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(id)));
        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", id).header("Authorization", m.delegado().bearer()))
                .andExpect(status().isNoContent());

        // La cuenta pasa a Juan, con su nombre y su documento; la persona anterior se borra.
        var persona = personaDe(u);
        assertThat(persona.getId()).isEqualTo(juan);
        assertThat(persona.nombreCompleto()).isEqualTo(NOMBRE_REGISTRADO);
        assertThat(persona.getNumeroDocumento()).isEqualTo(suDocumento);
        assertThat(personaRepository.findById(suPersona)).isEmpty();
        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipos[*].nombre").value(hasItem(m.inscripcion().getEquipo().getNombre())));

        // Ya tiene cuenta: deja de salir entre los miembros sin cuenta.
        mvc.perform(get("/api/supercopa/vinculos/equipo-torneo/{id}/miembros-sin-cuenta", m.inscripcion().getId())
                        .header("Authorization", usuarioRegistrado().bearer()))
                .andExpect(jsonPath("$[*].personaId").value(not(hasItem(juan.toString()))));
    }

    @Test
    void elAdmin_tambienEntregaElHistorial() throws Exception {
        var m = mundo();
        UUID juan = agregarSoloConNombre(m, "Juan Para El Admin");
        var u = usuarioRegistrado();
        String id = objectMapper.readTree(reclamar(u, juan).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        var admin = cuentaConRoles(Rol.ADMINISTRADOR);
        mvc.perform(get("/api/supercopa/vinculos/pendientes").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[*].id").value(hasItem(id)));
        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", id).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());

        assertThat(personaDe(u).getId()).isEqualTo(juan);
    }

    @Test
    void reclamarAQuienYaTieneCuenta_409PersonaYaVinculada() throws Exception {
        var otro = usuarioRegistrado();

        reclamar(usuarioRegistrado(), personaDe(otro).getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERSONA_YA_VINCULADA"));
    }

    @Test
    void reclamarAQuienNoEstaEnNingunPlantel_409SinHistorial() throws Exception {
        var suelta = personaRepository.save(Persona.builder().nombres("Nadie En Plantel").build());

        reclamar(usuarioRegistrado(), suelta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIN_HISTORIAL"));
    }

    @Test
    void reclamarAQuienTieneOtroDocumento_409DocumentosDistintos() throws Exception {
        var m = mundo();
        String respuesta = agregar(m, Map.of("nombre", "Con Su Documento", "numeroDocumento", documentoAlAzar()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conDocumento = UUID.fromString(objectMapper.readTree(respuesta).get("personaId").asText());

        reclamar(usuarioRegistrado(), conDocumento)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTOS_DISTINTOS"));
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private record Mundo(Usuario delegado, Torneo torneo, EquipoTorneo inscripcion) {
    }

    private Mundo mundo() {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
        Usuario delegado = cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR);
        UUID delegadoId = personaDe(delegado).getId();
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + delegadoId.toString().substring(0, 8))
                .delegadoPersonaId(delegadoId)
                .build());
        var inscripcion = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(delegadoId)
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build());
        return new Mundo(delegado, torneo, inscripcion);
    }

    private ResultActions agregar(Mundo m, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros", m.inscripcion().getId())
                .header("Authorization", m.delegado().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    private UUID agregarSoloConNombre(Mundo m, String nombre) throws Exception {
        String respuesta = agregar(m, Map.of("nombre", nombre)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(respuesta).get("personaId").asText());
    }

    private ResultActions reclamar(Usuario u, UUID personaId) throws Exception {
        return mvc.perform(post("/api/supercopa/vinculos/reclamar")
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("personaId", personaId))));
    }

    private Usuario usuarioRegistrado() throws Exception {
        var u = usuario();
        registrar(u);
        return u;
    }

    private Persona personaDe(Usuario u) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona();
    }
}

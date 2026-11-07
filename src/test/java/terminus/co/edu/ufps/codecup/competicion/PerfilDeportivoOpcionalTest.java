package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
import terminus.co.edu.ufps.codecup.competicion.model.Jugador;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0020: el perfil deportivo (altura, pierna hábil, posición) es opcional.
 * Nada lo exige para pedir unirse a un equipo ni para inscribir el equipo, y se
 * puede guardar a medias desde "Mi perfil".
 *
 * <p>El H2 se comparte entre clases: cada test arma su propio torneo con documentos al azar.
 */
class PerfilDeportivoOpcionalTest extends AuthTestSupport {

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired JugadorRepository jugadorRepository;
    @Autowired JugadorEquipoRepository jugadorEquipoRepository;

    // ── Criterios 1 y 2: pedir unirse a un equipo ───────────────────────

    @Test
    void solicitarIngreso_sinPerfil_seCrea_yAlAprobarEntraConSnapshotNull() throws Exception {
        var torneo = torneoPublicado();
        var delegado = delegadoConEquipo();
        var inscripcion = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(delegado.equipo()).delegadoCedula(delegado.cedula())
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build());
        var jugador = usuario();
        String doc = registrar(jugador);

        String respuesta = mvc.perform(post("/api/supercopa/solicitudes")
                        .header("Authorization", jugador.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("equipoTorneoId", inscripcion.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.cedula").value(doc))
                .andReturn().getResponse().getContentAsString();
        String solicitudId = objectMapper.readTree(respuesta).get("id").asText();

        var local = jugadorRepository.findById(doc).orElseThrow();
        assertThat(local.getAlturaCm()).isNull();
        assertThat(local.getPiernaHabil()).isNull();
        assertThat(local.getPosicion()).isNull();

        mvc.perform(post("/api/supercopa/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", delegado.usuario().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));

        var je = jugadorEquipoRepository.findByCedulaAndTorneoId(doc, torneo.getId()).orElseThrow();
        assertThat(je.getAlturaCmSnapshot()).isNull();
        assertThat(je.getPiernaHabilSnapshot()).isNull();
        assertThat(je.getPosicionSnapshot()).isNull();
    }

    // ── Criterios 3 y 4: inscribir el equipo ────────────────────────────

    @Test
    void inscribir_delegadoSinPerfil_inscribeYLoSumaAlPlantel() throws Exception {
        var torneo = torneoPublicado();
        var delegado = delegadoConEquipo();

        inscribir(delegado, torneo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoInscripcion").value("PENDIENTE_PAGO"));

        assertThat(equipoTorneoRepository.findByTorneoIdAndDelegadoCedula(torneo.getId(), delegado.cedula()))
                .isPresent();
        var je = jugadorEquipoRepository.findByCedulaAndTorneoId(delegado.cedula(), torneo.getId()).orElseThrow();
        assertThat(je.getAlturaCmSnapshot()).isNull();
        assertThat(je.getPiernaHabilSnapshot()).isNull();
        assertThat(je.getPosicionSnapshot()).isNull();
    }

    @Test
    void inscribir_delegadoConPerfil_copiaElSnapshot() throws Exception {
        var torneo = torneoPublicado();
        var delegado = delegadoConEquipo();
        jugadorRepository.save(Jugador.builder()
                .cedula(delegado.cedula()).nombre("Admin Prueba")
                .alturaCm(181).piernaHabil("IZQUIERDA").posicion("PORTERO")
                .build());

        inscribir(delegado, torneo).andExpect(status().isOk());

        var je = jugadorEquipoRepository.findByCedulaAndTorneoId(delegado.cedula(), torneo.getId()).orElseThrow();
        assertThat(je.getAlturaCmSnapshot()).isEqualTo(181);
        assertThat(je.getPiernaHabilSnapshot()).isEqualTo("IZQUIERDA");
        assertThat(je.getPosicionSnapshot()).isEqualTo("PORTERO");
    }

    // ── Criterios 5 y 6: guardar el perfil desde "Mi perfil" ────────────

    @Test
    void actualizarPerfil_parcial_guardaLoQueLlega() throws Exception {
        var u = usuario();
        String doc = registrar(u);

        // Solo la altura.
        actualizarPerfil(u, perfil(180, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alturaCm").value(180))
                .andExpect(jsonPath("$.piernaHabil").doesNotExist())
                .andExpect(jsonPath("$.posicion").doesNotExist());

        // Solo la posición: el PUT reemplaza, así que la altura queda sin indicar.
        actualizarPerfil(u, perfil(null, null, "PORTERO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alturaCm").doesNotExist())
                .andExpect(jsonPath("$.posicion").value("PORTERO"));

        // Todo vacío: se borra.
        actualizarPerfil(u, Map.of()).andExpect(status().isOk());

        var local = jugadorRepository.findById(doc).orElseThrow();
        assertThat(local.getAlturaCm()).isNull();
        assertThat(local.getPiernaHabil()).isNull();
        assertThat(local.getPosicion()).isNull();
    }

    @Test
    void actualizarPerfil_valorInvalido_400_yNoGuarda() throws Exception {
        var u = usuario();
        String doc = registrar(u);
        actualizarPerfil(u, perfil(175, "DERECHA", "DEFENSA")).andExpect(status().isOk());

        actualizarPerfil(u, perfil(99, null, null)).andExpect(status().isBadRequest());
        actualizarPerfil(u, perfil(251, null, null)).andExpect(status().isBadRequest());
        actualizarPerfil(u, perfil(null, "ZURDA", null)).andExpect(status().isBadRequest());
        actualizarPerfil(u, perfil(null, null, "ARQUERO")).andExpect(status().isBadRequest());

        var local = jugadorRepository.findById(doc).orElseThrow();
        assertThat(local.getAlturaCm()).isEqualTo(175);
        assertThat(local.getPiernaHabil()).isEqualTo("DERECHA");
        assertThat(local.getPosicion()).isEqualTo("DEFENSA");

        // Los bordes valen.
        actualizarPerfil(u, perfil(100, null, null)).andExpect(status().isOk());
        actualizarPerfil(u, perfil(250, null, null)).andExpect(status().isOk());
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    /** Un delegado (con JUGADOR) que ya creó su equipo. No tiene fila de jugador ni perfil. */
    private record Delegado(Usuario usuario, String cedula, Equipo equipo) {
    }

    private Delegado delegadoConEquipo() {
        Usuario u = cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR);
        String cedula = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub())
                .orElseThrow().getPersona().getNumeroDocumento();
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + cedula)
                .delegadoCedula(cedula)
                .build());
        return new Delegado(u, cedula, equipo);
    }

    private Torneo torneoPublicado() {
        return torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
    }

    private ResultActions inscribir(Delegado d, Torneo t) throws Exception {
        return mvc.perform(post("/api/supercopa/delegado/torneos/{id}/inscribir", t.getId())
                .header("Authorization", d.usuario().bearer()));
    }

    private ResultActions actualizarPerfil(Usuario u, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(put("/api/supercopa/mi-perfil")
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    private static Map<String, Object> perfil(Integer alturaCm, String piernaHabil, String posicion) {
        var c = new HashMap<String, Object>();
        c.put("alturaCm", alturaCm);
        c.put("piernaHabil", piernaHabil);
        c.put("posicion", posicion);
        return c;
    }
}

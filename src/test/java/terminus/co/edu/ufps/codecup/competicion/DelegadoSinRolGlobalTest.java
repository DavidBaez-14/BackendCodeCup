package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;

/**
 * Spec 0026: cualquiera arma su equipo y lo inscribe sin pedir el rol de delegado; el delegado es
 * quien figura en la inscripción. El admin también suma equipos por invitación (camino b) y puede
 * reasignar el delegado.
 */
class DelegadoSinRolGlobalTest extends AuthTestSupport {

    private static final String DELEGADO = "/api/supercopa/delegado";

    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired JugadorEquipoRepository jugadorEquipoRepository;

    // ── CA-1: inscribir sin rol ─────────────────────────────────────────

    @Test
    void ca1_soloJugador_inscribeUnEquipoNuevo_yQuedaComoDelegadoEnElPlantel() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        var ana = registrado();

        UUID inscripcion = inscribir(ana, torneo.getId(), Map.of("nombreEquipo", "Los Pibes FC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoInscripcion").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.equipoNombre").value("Los Pibes FC"))
                .andExpect(jsonPath("$.delegadoPersonaId").value(personaDe(ana).toString()))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        mvc.perform(get(DELEGADO + "/equipo-torneo/{id}/miembros", inscripcion).header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.esDelegado == true)].nombre").value(contains("Ana María Pérez")));
        mvc.perform(get(DELEGADO + "/equipos").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$[*].nombre").value(contains("Los Pibes FC")));
        // No ganó ningún rol global.
        mvc.perform(get("/api/auth/me").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.roles").value(contains("JUGADOR")));
    }

    @Test
    void ca1_variosEquipos_hayQueElegir_yNoSeRepiteElNombre() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        var ana = registrado();
        crearEquipo(ana, "Fútbol Sala Ana").andExpect(status().isOk());
        String voley = crearEquipo(ana, "Voleibol Ana").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        crearEquipo(ana, "voleibol ana")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPO_DUPLICADO"));

        inscribir(ana, torneo.getId(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EQUIPO_REQUERIDO"));
        inscribir(ana, torneo.getId(), Map.of("equipoId", id(voley)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipoNombre").value("Voleibol Ana"));

        // El equipo de otra persona no se puede inscribir.
        var beto = registrado();
        inscribir(beto, torneoDePrueba(EstadoTorneo.PUBLICADO).getId(), Map.of("equipoId", id(voley)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EQUIPO_AJENO"));
    }

    @Test
    void ca1_torneoSinInscripcionesAbiertas_409() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.EN_CURSO);
        inscribir(registrado(), torneo.getId(), Map.of("nombreEquipo", "Tarde FC"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSCRIPCIONES_CERRADAS"));
    }

    // ── CA-2: el delegado gestiona solo su inscripción ──────────────────

    @Test
    void ca2_delegadoSinRol_gestionaSuPlantelSolicitudesYComprobante_yNoLosAjenos() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        var ana = registrado();
        var beto = registrado();
        UUID deAna = inscribir(ana, torneo.getId(), Map.of("nombreEquipo", "Equipo Ana"))
                .andReturn().getResponse().getContentAsString().transform(this::id);
        inscribir(beto, torneo.getId(), Map.of("nombreEquipo", "Equipo Beto")).andExpect(status().isOk());

        mvc.perform(get(DELEGADO + "/equipo-torneo/{id}/miembros", deAna).header("Authorization", beto.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_DELEGADO_DE_LA_INSCRIPCION"));

        // Un jugador pide unirse; Ana (sin rol DELEGADO) lo ve y lo aprueba; Beto no puede.
        var carla = registrado();
        String solicitud = mvc.perform(post("/api/supercopa/solicitudes").header("Authorization", carla.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("equipoTorneoId", deAna))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/supercopa/solicitudes/pendientes").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/supercopa/solicitudes/{id}/aprobar", id(solicitud)).header("Authorization", beto.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_DELEGADO_DE_LA_INSCRIPCION"));
        mvc.perform(post("/api/supercopa/solicitudes/{id}/aprobar", id(solicitud)).header("Authorization", ana.bearer()))
                .andExpect(status().isOk());

        // Sube el comprobante de su inscripción sin rol global.
        mvc.perform(post("/api/finanzas/comprobantes").header("Authorization", ana.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tipo", "INSCRIPCION", "referenciaId", deAna, "urlArchivo", deAna + "/comprobante"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));
        mvc.perform(get("/api/finanzas/comprobantes/mis-comprobantes").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ── CA-3: cupo por invitación (camino b) ────────────────────────────

    @Test
    void ca3_cupoSinCobro_quienAceptaQuedaComoDelegadoDeUnEquipoAprobado() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        var admin = registrado();
        hacerAdmin(admin, torneo.getId());

        String inv = crearCupo(admin, torneo.getId(), Map.of("nombreEquipo", "Los Invitados"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("DELEGADO"))
                .andExpect(jsonPath("$.datoExtra").value("Los Invitados"))
                .andExpect(jsonPath("$.ambitoDescripcion").value("Los Invitados · " + torneo.getNombre()))
                .andReturn().getResponse().getContentAsString();
        var invitado = registrado();
        aceptar(invitado, token(inv)).andExpect(status().isOk());

        var et = equipoTorneoRepository.findByTorneoIdAndDelegadoPersonaId(torneo.getId(), personaDe(invitado)).orElseThrow();
        assertThat(et.getEstadoInscripcion()).isEqualTo(EstadoInscripcion.APROBADO);
        assertThat(et.getAprobadoPorCuentaId()).isEqualTo(cuentaIdDe(admin));
        mvc.perform(get("/api/supercopa/yo/equipos").header("Authorization", invitado.bearer()))
                .andExpect(jsonPath("$[0].equipo").value("Los Invitados"))
                .andExpect(jsonPath("$[0].rol").value("DELEGADO"))
                .andExpect(jsonPath("$[0].estadoInscripcion").value("APROBADO"));
        assertThat(jugadorEquipoRepository.existsByPersonaIdAndTorneoIdAndEstado(
                personaDe(invitado), torneo.getId(), EstadoMembresia.ACTIVO)).isTrue();
    }

    @Test
    void ca3_cupoConCobro_quedaPendienteDePago_ySiYaDelegaNoSeAcepta() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        torneo.setMontoInscripcion(new BigDecimal("30000"));
        soporteTorneos.save(torneo);
        var admin = registrado();
        hacerAdmin(admin, torneo.getId());
        var invitado = registrado();

        aceptar(invitado, token(crearCupo(admin, torneo.getId(), Map.of("nombreEquipo", "Con Cuota"))
                .andReturn().getResponse().getContentAsString())).andExpect(status().isOk());
        assertThat(equipoTorneoRepository.findByTorneoIdAndDelegadoPersonaId(torneo.getId(), personaDe(invitado))
                .orElseThrow().getEstadoInscripcion()).isEqualTo(EstadoInscripcion.PENDIENTE_PAGO);

        // Un segundo cupo para la misma persona no se acepta y la invitación sigue pendiente.
        String token = token(crearCupo(admin, torneo.getId(), Map.of("nombreEquipo", "Otro Cupo"))
                .andReturn().getResponse().getContentAsString());
        aceptar(invitado, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("YA_INSCRITO"));
        mvc.perform(get("/api/auth/invitaciones/{token}", token))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void ca3_soloElAdminCreaCupos() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        crearCupo(registrado(), torneo.getId(), Map.of("nombreEquipo", "Intruso"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));
    }

    // ── CA-4: reasignar el delegado ─────────────────────────────────────

    @Test
    void ca4_reasignar_elNuevoGestiona_yElAnteriorSigueEnElPlantelSinAcceso() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.PUBLICADO);
        var admin = registrado();
        hacerAdmin(admin, torneo.getId());
        var ana = registrado();
        UUID inscripcion = inscribir(ana, torneo.getId(), Map.of("nombreEquipo", "Reasignables"))
                .andReturn().getResponse().getContentAsString().transform(this::id);
        var beto = registrado();
        String doc = documentoDe(beto);
        mvc.perform(post(DELEGADO + "/equipo-torneo/{id}/miembros", inscripcion).header("Authorization", ana.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("numeroDocumento", doc))))
                .andExpect(status().isOk());
        String sinCuenta = mvc.perform(post(DELEGADO + "/equipo-torneo/{id}/miembros", inscripcion)
                        .header("Authorization", ana.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nombre", "Sin Cuenta"))))
                .andReturn().getResponse().getContentAsString();

        reasignar(admin, torneo, inscripcion, UUID.fromString(objectMapper.readTree(sinCuenta).get("personaId").asText()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELEGADO_SIN_CUENTA"));
        reasignar(admin, torneo, inscripcion, personaDe(registrado()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELEGADO_FUERA_DEL_PLANTEL"));
        reasignar(admin, torneo, inscripcion, personaDe(beto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegadoPersonaId").value(personaDe(beto).toString()));

        mvc.perform(get(DELEGADO + "/equipo-torneo/{id}/miembros", inscripcion).header("Authorization", beto.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get(DELEGADO + "/equipo-torneo/{id}/miembros", inscripcion).header("Authorization", ana.bearer()))
                .andExpect(status().isForbidden());
        assertThat(jugadorEquipoRepository.findByPersonaIdAndEquipoTorneoId(personaDe(ana), inscripcion).orElseThrow()
                .getEstado()).isEqualTo(EstadoMembresia.ACTIVO);
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private Usuario registrado() throws Exception {
        var u = usuario();
        registrar(u);
        return u;
    }

    private UUID personaDe(Usuario u) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona().getId();
    }

    private String documentoDe(Usuario u) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona().getNumeroDocumento();
    }

    private ResultActions crearEquipo(Usuario u, String nombre) throws Exception {
        return mvc.perform(post(DELEGADO + "/equipos").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nombre", nombre))));
    }

    private ResultActions inscribir(Usuario u, UUID torneoId, Map<String, Object> cuerpo) throws Exception {
        var pedido = post(DELEGADO + "/torneos/{id}/inscribir", torneoId).header("Authorization", u.bearer());
        if (cuerpo != null) pedido.contentType(MediaType.APPLICATION_JSON).content(json(cuerpo));
        return mvc.perform(pedido);
    }

    private ResultActions crearCupo(Usuario u, UUID torneoId, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/supercopa/admin/torneos/{id}/cupos", torneoId).header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private ResultActions aceptar(Usuario u, String token) throws Exception {
        return mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", token).header("Authorization", u.bearer()));
    }

    private ResultActions reasignar(Usuario admin, Torneo t, UUID inscripcion, UUID personaId) throws Exception {
        return mvc.perform(put("/api/supercopa/admin/torneos/{t}/inscripciones/{i}/delegado", t.getId(), inscripcion)
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("personaId", personaId))));
    }

    private UUID id(String json) {
        try {
            return UUID.fromString(objectMapper.readTree(json).get("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String token(String json) throws Exception {
        return objectMapper.readTree(json).get("token").asText();
    }
}

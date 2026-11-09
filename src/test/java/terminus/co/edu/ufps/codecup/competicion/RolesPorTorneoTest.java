package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0024: no hay administradores globales. Cada creador administra solo los torneos que crea o
 * a los que lo invitan como co-admin, y las bandejas solo traen lo de sus torneos.
 */
class RolesPorTorneoTest extends AuthTestSupport {

    private static final String TORNEOS = "/api/supercopa/admin/torneos";

    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired ComprobanteRepository comprobanteRepository;

    // ── CA-1: crear sin rol global ──────────────────────────────────────

    @Test
    void ca1_unJugadorCreaUnTorneo_yQuedaComoSuAdmin() throws Exception {
        var ana = registrado();

        UUID id = crearTorneo(ana, "Relámpago de Ana");

        assertThat(soporteRolesTorneo.existsByTorneoIdAndCuentaIdAndRol(id, cuentaIdDe(ana), RolTorneo.ADMIN)).isTrue();
        mvc.perform(get("/api/supercopa/creador/torneos").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].estado").value("BORRADOR"))
                .andExpect(jsonPath("$[0].pendientes.inscripciones").value(0));
    }

    @Test
    void ca1_sinRegistro_404CuentaNoRegistrada() throws Exception {
        var sinRegistro = usuario();
        mvc.perform(post(TORNEOS).header("Authorization", sinRegistro.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "X", "edicion", 2026))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUENTA_NO_REGISTRADA"));
    }

    // ── CA-2: cada creador ve y opera solo lo suyo ──────────────────────

    @Test
    void ca2_misTorneos_soloLosPropios_yLosAjenosResponden403() throws Exception {
        var a = registrado();
        var b = registrado();
        UUID deA = crearTorneo(a, "Copa A");
        UUID deB = crearTorneo(b, "Copa B");

        mvc.perform(get("/api/supercopa/creador/torneos").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$[*].id").value(contains(deA.toString())));
        mvc.perform(get(TORNEOS).header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(deA.toString())));

        var inscripcionDeB = inscripcion(deB);
        var pedidos = new org.springframework.test.web.servlet.RequestBuilder[] {
            get(TORNEOS + "/{id}/configuracion", deB),
            put(TORNEOS + "/{id}/configuracion", deB).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"formato\":\"LIGA\"}"),
            post(TORNEOS + "/{id}/publicar", deB),
            get(TORNEOS + "/{id}/inscripciones", deB),
            post(TORNEOS + "/{id}/inscripciones/{et}/aprobar", deB, inscripcionDeB.getId()),
            post(TORNEOS + "/{id}/fixture", deB),
            get(TORNEOS + "/{id}/premios", deB),
            get(TORNEOS + "/{id}/roles", deB),
            get("/api/finanzas/multas/activas").param("torneoId", deB.toString()),
            get("/api/finanzas/multas/torneo/{id}/bolsa", deB),
        };
        for (var pedido : pedidos) {
            mvc.perform(conToken(pedido, a))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));
        }
        // Nada cambió en el torneo de B.
        assertThat(equipoTorneoRepository.findById(inscripcionDeB.getId()).orElseThrow().getEstadoInscripcion())
                .isEqualTo(EstadoInscripcion.PENDIENTE_PAGO);
    }

    @Test
    void ca2_unTorneoQueNoExiste_404() throws Exception {
        var a = registrado();
        mvc.perform(get(TORNEOS + "/{id}/configuracion", UUID.randomUUID()).header("Authorization", a.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
    }

    @Test
    void ca2_comprobanteDeOtroTorneo_noSeApruebaNiSeRechaza() throws Exception {
        var a = registrado();
        var b = registrado();
        crearTorneo(a, "Copa A");
        UUID deB = crearTorneo(b, "Copa B");
        var comprobante = comprobanteDe(inscripcion(deB));

        mvc.perform(post("/api/finanzas/comprobantes/{id}/aprobar", comprobante.getId()).header("Authorization", a.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));
        mvc.perform(post("/api/finanzas/comprobantes/{id}/rechazar", comprobante.getId())
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("motivo", "no"))))
                .andExpect(status().isForbidden());
        assertThat(comprobanteRepository.findById(comprobante.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoComprobante.PENDIENTE_REVISION);

        // B, que sí administra, lo aprueba y la inscripción queda aprobada.
        mvc.perform(post("/api/finanzas/comprobantes/{id}/aprobar", comprobante.getId()).header("Authorization", b.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    // ── CA-3: co-admins por invitación ──────────────────────────────────

    @Test
    void ca3_coAdminInvitadoPorCorreo_administraEseTorneoYNingunOtro() throws Exception {
        var creador = registrado();
        UUID torneo = crearTorneo(creador, "Copa Compartida");
        UUID otro = crearTorneo(registrado(), "Copa Ajena");
        var invitado = registrado();

        String respuesta = invitar(creador, torneo, Map.of("rol", "ADMIN", "correo", invitado.correo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ambito").value("TORNEO"))
                .andExpect(jsonPath("$.ambitoId").value(torneo.toString()))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.enlace").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(respuesta).get("token").asText();

        // Antes de aceptar, cualquiera con el enlace ve qué es (sin sesión), con el correo enmascarado.
        mvc.perform(get("/api/auth/invitaciones/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambitoDescripcion").value("Copa Compartida"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.invitadoPor").value("Ana María Pérez"))
                .andExpect(jsonPath("$.paraCorreo").value(invitado.correo().charAt(0) + "***@correo.test"));

        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", token).header("Authorization", invitado.bearer()))
                .andExpect(status().isOk());

        mvc.perform(get(TORNEOS + "/{id}/configuracion", torneo).header("Authorization", invitado.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get(TORNEOS + "/{id}/configuracion", otro).header("Authorization", invitado.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get(TORNEOS + "/{id}/roles", torneo).header("Authorization", creador.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.esTuyo == true)].nombre").value(contains("Ana María Pérez")));
        // El co-admin no ganó ningún rol global.
        mvc.perform(get("/api/auth/me").header("Authorization", invitado.bearer()))
                .andExpect(jsonPath("$.roles").value(not(hasItem("ADMINISTRADOR"))));
    }

    @Test
    void ca3_invitacionPorEnlace_laAceptaLaPrimeraCuenta_yNoSeReusa() throws Exception {
        var creador = registrado();
        UUID torneo = crearTorneo(creador, "Copa Enlace");

        String respuesta = invitar(creador, torneo, Map.of("rol", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(respuesta).get("token").asText();

        mvc.perform(get(TORNEOS + "/{id}/invitaciones", torneo).header("Authorization", creador.bearer()))
                .andExpect(jsonPath("$[*].token").value(hasItem(token)));

        var primero = registrado();
        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", token).header("Authorization", primero.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", token).header("Authorization", registrado().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITACION_NO_VIGENTE"));

        assertThat(soporteRolesTorneo.existsByTorneoIdAndCuentaIdAndRol(torneo, cuentaIdDe(primero), RolTorneo.ADMIN)).isTrue();
        // Ya no está pendiente, y las de torneo no salen en el panel de Usuarios.
        mvc.perform(get(TORNEOS + "/{id}/invitaciones", torneo).header("Authorization", creador.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/admin/invitaciones").header("Authorization", cuentaConRoles(Rol.SUPERADMIN).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].token").value(not(hasItem(token))));
    }

    @Test
    void ca3_soloUnAdminDelTorneoInvita_yRevocar() throws Exception {
        var creador = registrado();
        UUID torneo = crearTorneo(creador, "Copa Revocar");

        invitar(registrado(), torneo, Map.of("rol", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));

        String id = objectMapper.readTree(invitar(creador, torneo, Map.of("rol", "ADMIN"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(delete(TORNEOS + "/{id}/invitaciones/{inv}", torneo, id).header("Authorization", creador.bearer()))
                .andExpect(status().isNoContent());
        // Revocar una invitación de otro torneo no la encuentra.
        UUID otro = crearTorneo(creador, "Copa Otra");
        mvc.perform(delete(TORNEOS + "/{id}/invitaciones/{inv}", otro, id).header("Authorization", creador.bearer()))
                .andExpect(status().isNotFound());
    }

    // ── CA-4: bandejas por torneo ───────────────────────────────────────

    @Test
    void ca4_comprobantesPendientes_soloDeSusTorneos() throws Exception {
        var a = registrado();
        var b = registrado();
        UUID deA = crearTorneo(a, "Copa A");
        UUID deB = crearTorneo(b, "Copa B");
        var deLaA = comprobanteDe(inscripcion(deA));
        var deLaB = comprobanteDe(inscripcion(deB));

        mvc.perform(get("/api/finanzas/comprobantes/pendientes").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(deLaA.getId().toString())))
                .andExpect(jsonPath("$[0].torneoId").value(deA.toString()))
                .andExpect(jsonPath("$[0].torneoNombre").value("Copa A"))
                .andExpect(jsonPath("$[0].equipoNombre").isNotEmpty());
        mvc.perform(get("/api/finanzas/comprobantes/pendientes").param("torneoId", deB.toString())
                        .header("Authorization", a.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));
        mvc.perform(get("/api/finanzas/comprobantes/pendientes").header("Authorization", registrado().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/supercopa/creador/torneos").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$[0].pendientes.inscripciones").value(1))
                .andExpect(jsonPath("$[0].pendientes.comprobantes").value(1));
        assertThat(deLaB.getId()).isNotNull();
    }

    // ── CA-5: el último admin no se va ──────────────────────────────────

    @Test
    void ca5_ultimoAdmin_409_yConOtroAdminSiSePuedeQuitar() throws Exception {
        var creador = registrado();
        UUID torneo = crearTorneo(creador, "Copa Último");
        UUID rolCreador = soporteRolesTorneo.findByTorneoIdAndCuentaIdAndRol(torneo, cuentaIdDe(creador), RolTorneo.ADMIN)
                .orElseThrow().getId();

        mvc.perform(delete(TORNEOS + "/{id}/roles/{rol}", torneo, rolCreador).header("Authorization", creador.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ULTIMO_ADMIN_DEL_TORNEO"));

        var coAdmin = registrado();
        hacerAdmin(coAdmin, torneo);
        mvc.perform(delete(TORNEOS + "/{id}/roles/{rol}", torneo, rolCreador).header("Authorization", coAdmin.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(TORNEOS + "/{id}/configuracion", torneo).header("Authorization", creador.bearer()))
                .andExpect(status().isForbidden());
    }

    // ── CA-7: SUPERADMIN no entra a torneos ajenos ──────────────────────

    @Test
    void ca7_superadmin_entraAlPanelDeUsuarios_peroNoAUnTorneoAjeno() throws Exception {
        var superadmin = cuentaConRoles(Rol.ADMINISTRADOR, Rol.JUGADOR);
        UUID ajeno = crearTorneo(registrado(), "Copa Ajena");

        mvc.perform(get("/api/admin/cuentas").header("Authorization", superadmin.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get(TORNEOS + "/{id}/configuracion", ajeno).header("Authorization", superadmin.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));
        mvc.perform(get(TORNEOS).header("Authorization", superadmin.bearer()))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(ajeno.toString()))));
    }

    @Test
    void ca7_adminGlobalSinSuperadmin_yaNoEntraAlPanel() throws Exception {
        var soloAdministrador = cuentaConRolesExactos(Rol.ADMINISTRADOR, Rol.JUGADOR);
        mvc.perform(get("/api/admin/cuentas").header("Authorization", soloAdministrador.bearer()))
                .andExpect(status().isForbidden());
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private Usuario registrado() throws Exception {
        var u = usuario();
        registrar(u);
        return u;
    }

    private UUID crearTorneo(Usuario u, String nombre) throws Exception {
        String respuesta = mvc.perform(post(TORNEOS).header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", nombre, "edicion", 2026))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(respuesta).get("id").asText());
    }

    private ResultActions invitar(Usuario quien, UUID torneoId, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post(TORNEOS + "/{id}/invitaciones", torneoId)
                .header("Authorization", quien.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    /** Una inscripción PENDIENTE_PAGO de un delegado cualquiera en el torneo. */
    private EquipoTorneo inscripcion(UUID torneoId) {
        Usuario delegado = cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR);
        UUID personaId = cuentaRepository.findConPersona(
                terminus.co.edu.ufps.codecup.identidad.model.Cuenta.PROVEEDOR_SUPABASE, delegado.sub())
                .orElseThrow().getPersona().getId();
        Torneo torneo = soporteTorneos.findById(torneoId).orElseThrow();
        if (torneo.getEstado() == EstadoTorneo.BORRADOR) {
            torneo.setEstado(EstadoTorneo.PUBLICADO);
            soporteTorneos.save(torneo);
        }
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + personaId.toString().substring(0, 8))
                .delegadoPersonaId(personaId)
                .build());
        return equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(personaId)
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build());
    }

    private Comprobante comprobanteDe(EquipoTorneo et) {
        return comprobanteRepository.save(Comprobante.builder()
                .tipo(TipoComprobante.INSCRIPCION)
                .referenciaId(et.getId())
                .urlArchivo(et.getId() + "/comprobante")
                .subidoPorCuentaId(cuentaIdDe(cuentaConRoles(Rol.JUGADOR)))
                .estado(EstadoComprobante.PENDIENTE_REVISION)
                .fechaSubida(LocalDateTime.now())
                .build());
    }

    private org.springframework.test.web.servlet.RequestBuilder conToken(
            org.springframework.test.web.servlet.RequestBuilder pedido, Usuario u) {
        return ((org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) pedido)
                .header("Authorization", u.bearer());
    }
}

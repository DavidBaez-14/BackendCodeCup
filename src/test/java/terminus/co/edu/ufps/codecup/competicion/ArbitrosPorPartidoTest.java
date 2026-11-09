package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.codecup.finanzas.model.Multa;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoSancion;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0025: los árbitros se asignan por partido. Un árbitro del torneo solo opera todos los
 * partidos si el admin le da ese permiso, y nunca los de otro torneo.
 */
class ArbitrosPorPartidoTest extends AuthTestSupport {

    private static final String PARTIDOS = "/api/supercopa/admin/partidos";

    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired JugadorEquipoRepository jugadorEquipoRepository;
    @Autowired EventoPartidoRepository eventoRepository;
    @Autowired MultaRepository multaRepository;

    // ── CA-1 y CA-2: asignado sí, sin asignación no ─────────────────────

    @Test
    void ca1_arbitroAsignado_operaLaPlanilla_comoAntes() throws Exception {
        var m = mundo();
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);
        asignarArbitro(arbitro, m.partido());

        alinear(arbitro, m, m.jugadorLocal()).andExpect(status().isOk());
        evento(arbitro, m, m.jugadorLocal(), "GOL").andExpect(status().isOk());
        mvc.perform(post(PARTIDOS + "/{id}/cerrar", m.partido().getId()).header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.golesLocal").value(1));
    }

    @Test
    void ca2_arbitroDelTorneoSinAsignacion_403_yNoCambiaNada() throws Exception {
        var m = mundo();
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);
        var arbitroGlobal = cuentaConRoles(Rol.ARBITRO, Rol.JUGADOR);

        for (var u : new Usuario[] {arbitro, arbitroGlobal}) {
            evento(u, m, m.jugadorLocal(), "AMARILLA")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NO_ARBITRO_DEL_PARTIDO"));
            alinear(u, m, m.jugadorLocal()).andExpect(status().isForbidden());
            mvc.perform(post(PARTIDOS + "/{id}/cerrar", m.partido().getId()).header("Authorization", u.bearer()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/supercopa/arbitro/partidos/{id}/planilla", m.partido().getId())
                            .header("Authorization", u.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NO_ARBITRO_DEL_PARTIDO"));
        }
        assertThat(eventoRepository.findByPartidoIdOrderByOrdenAsc(m.partido().getId())).isEmpty();
    }

    // ── CA-3: todos los partidos de su torneo y ninguno de otro ─────────

    @Test
    void ca3_conTodosLosPartidos_operaCualquierPartidoDelTorneo_peroNoDeOtro() throws Exception {
        var m = mundo();
        var otro = mundo();
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), true);

        evento(arbitro, m, m.jugadorLocal(), "AMARILLA").andExpect(status().isOk());
        evento(arbitro, otro, otro.jugadorLocal(), "AMARILLA")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ARBITRO_DEL_PARTIDO"));
    }

    // ── CA-4: mis partidos ──────────────────────────────────────────────

    @Test
    void ca4_misPartidos_asignadosYDelTorneoConPermiso_enOrdenDeFecha() throws Exception {
        var m = mundo();          // partido asignado, mañana
        var conPermiso = mundo(); // torneo con permiso total
        var ajeno = mundo();      // no lo ve
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);
        asignarArbitro(arbitro, m.partido());
        hacerArbitro(arbitro, conPermiso.torneo().getId(), true);
        var temprano = partido(conPermiso, LocalDateTime.now().minusDays(2), EstadoPartido.FINALIZADO);
        partido(conPermiso, LocalDateTime.now().plusDays(3), EstadoPartido.DESCANSO);
        programar(m.partido(), "Cancha 2");

        mvc.perform(get("/api/supercopa/arbitro/partidos").header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].partidoId").value(temprano.getId().toString()))
                .andExpect(jsonPath("$[0].estado").value("FINALIZADO"))
                .andExpect(jsonPath("$[0].marcador.local").value(0))
                .andExpect(jsonPath("$[0].asignado").value(false))
                .andExpect(jsonPath("$[?(@.partidoId == '%s')].asignado", m.partido().getId()).value(contains(true)))
                .andExpect(jsonPath("$[?(@.partidoId == '%s')].lugar", m.partido().getId()).value(contains("Cancha 2")))
                .andExpect(jsonPath("$[?(@.partidoId == '%s')].fase", m.partido().getId()).value(contains("Grupo A · Fecha 1")))
                .andExpect(jsonPath("$[?(@.partidoId == '%s')].marcador", m.partido().getId()).value(contains((Object) null)))
                .andExpect(jsonPath("$[*].torneoId").value(org.hamcrest.Matchers.not(hasItem(ajeno.torneo().getId().toString()))));

        mvc.perform(get("/api/supercopa/arbitro/partidos").param("desde", LocalDate.now().toString())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/supercopa/arbitro/partidos").header("Authorization", cuentaConRoles(Rol.JUGADOR).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── Planilla en una sola respuesta ──────────────────────────────────

    @Test
    void planilla_traeLosDosPlanteles_conAptoYMotivos_yLosEventosEnOrden() throws Exception {
        var m = mundo();
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);
        asignarArbitro(arbitro, m.partido());
        // El visitante tiene una suspensión por roja vigente.
        multaRepository.save(Multa.builder()
                .personaId(m.jugadorVisitante()).partidoId(m.partido().getId())
                .equipoTorneoId(m.visitante().getId()).torneoId(m.torneo().getId())
                .tipoSancion(TipoSancion.ROJA).monto(BigDecimal.ZERO).estado(EstadoMulta.PAGADA)
                .partidosSuspension(1).partidosSuspensionRestantes(1).habilitadoManual(false)
                .fechaGeneracion(LocalDateTime.now())
                .build());
        alinear(arbitro, m, m.jugadorLocal()).andExpect(status().isOk());
        evento(arbitro, m, m.jugadorLocal(), "GOL").andExpect(status().isOk());
        evento(arbitro, m, m.jugadorLocal(), "AMARILLA").andExpect(status().isOk());

        mvc.perform(get("/api/supercopa/arbitro/partidos/{id}/planilla", m.partido().getId())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partido.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.partido.marcador.local").value(1))
                .andExpect(jsonPath("$.partido.asignado").value(true))
                .andExpect(jsonPath("$.tiposEvento").value(contains("GOL", "AMARILLA", "AZUL", "ROJA")))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.local.jugadores[?(@.personaId == '%s')].alineado", m.jugadorLocal()).value(contains(true)))
                .andExpect(jsonPath("$.local.jugadores[?(@.personaId == '%s')].goles", m.jugadorLocal()).value(contains(1)))
                .andExpect(jsonPath("$.local.jugadores[?(@.personaId == '%s')].apto", m.jugadorLocal()).value(contains(true)))
                .andExpect(jsonPath("$.visitante.jugadores[?(@.personaId == '%s')].apto", m.jugadorVisitante()).value(contains(false)))
                .andExpect(jsonPath("$.visitante.jugadores[?(@.personaId == '%s')].motivos[0]", m.jugadorVisitante())
                        .value(contains("SUSPENSION_ACTIVA")))
                .andExpect(jsonPath("$.eventos[*].tipoEvento").value(contains("GOL", "AMARILLA")));

        // El backend tampoco deja alinear al suspendido.
        alinear(arbitro, m, m.jugadorVisitante(), m.visitante()).andExpect(status().isBadRequest());
    }

    // ── CA-5: asignar ───────────────────────────────────────────────────

    @Test
    void ca5_asignar_soloArbitrosDelTorneo_yNuncaAQuienJuega() throws Exception {
        var m = mundo();
        var admin = cuentaConRoles(Rol.JUGADOR);
        hacerAdmin(admin, m.torneo().getId());
        var arbitro = cuentaConRoles(Rol.JUGADOR);

        asignar(admin, m.partido(), arbitro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ES_ARBITRO_DEL_TORNEO"));

        hacerArbitro(arbitro, m.torneo().getId(), false);
        asignar(admin, m.partido(), arbitro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cuentaId").value(contains(cuentaIdDe(arbitro).toString())));
        asignar(admin, m.partido(), arbitro).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/supercopa/admin/torneos/{id}/partidos", m.torneo().getId()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[0].arbitros[*].cuentaId").value(contains(cuentaIdDe(arbitro).toString())));

        // El delegado local juega en el partido: aunque sea árbitro del torneo, no lo puede arbitrar.
        hacerArbitro(m.delegadoLocal(), m.torneo().getId(), false);
        asignar(admin, m.partido(), m.delegadoLocal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARBITRO_EN_PLANTILLA"));

        // Solo el admin asigna.
        asignar(arbitro, m.partido(), arbitro)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));

        mvc.perform(delete(PARTIDOS + "/{id}/arbitros/{cuenta}", m.partido().getId(), cuentaIdDe(arbitro))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        evento(arbitro, m, m.jugadorLocal(), "GOL").andExpect(status().isForbidden());
    }

    // ── CA-6: invitación de árbitro y permiso total ─────────────────────

    @Test
    void ca6_invitacionDeArbitro_entraSinPermisoTotal_yElAdminSeLoDa() throws Exception {
        var m = mundo();
        var admin = cuentaConRoles(Rol.JUGADOR);
        hacerAdmin(admin, m.torneo().getId());
        var invitado = usuario();
        registrar(invitado);

        String respuesta = mvc.perform(post("/api/supercopa/admin/torneos/{id}/invitaciones", m.torneo().getId())
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("rol", "ARBITRO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ARBITRO"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(respuesta).get("token").asText();
        mvc.perform(post("/api/auth/invitaciones/{token}/aceptar", token).header("Authorization", invitado.bearer()))
                .andExpect(status().isOk());

        var rol = soporteRolesTorneo.findByTorneoIdAndCuentaIdAndRol(m.torneo().getId(), cuentaIdDe(invitado), RolTorneo.ARBITRO)
                .orElseThrow();
        assertThat(rol.getTodosLosPartidos()).isFalse();
        evento(invitado, m, m.jugadorLocal(), "GOL").andExpect(status().isForbidden());

        mvc.perform(put("/api/supercopa/admin/torneos/{id}/arbitros/{cuenta}/todos-los-partidos", m.torneo().getId(), cuentaIdDe(invitado))
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"todosLosPartidos\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todosLosPartidos").value(true))
                .andExpect(jsonPath("$.rol").value("ARBITRO"));
        evento(invitado, m, m.jugadorLocal(), "GOL").andExpect(status().isOk());

        mvc.perform(get("/api/supercopa/admin/torneos/{id}/roles", m.torneo().getId()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[*].rol").value(containsInAnyOrder("ADMIN", "ARBITRO")));
        // El árbitro ve el torneo en la lista de la web (admin/torneos), aunque no lo administre.
        mvc.perform(get("/api/supercopa/admin/torneos").header("Authorization", invitado.bearer()))
                .andExpect(jsonPath("$[*].id").value(contains(m.torneo().getId().toString())));
    }

    @Test
    void quitarElRolDeArbitro_leQuitaTambienSusPartidos() throws Exception {
        var m = mundo();
        var admin = cuentaConRoles(Rol.JUGADOR);
        hacerAdmin(admin, m.torneo().getId());
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);
        asignarArbitro(arbitro, m.partido());
        UUID rolId = soporteRolesTorneo.findByTorneoIdAndCuentaIdAndRol(m.torneo().getId(), cuentaIdDe(arbitro), RolTorneo.ARBITRO)
                .orElseThrow().getId();

        mvc.perform(delete("/api/supercopa/admin/torneos/{id}/roles/{rol}", m.torneo().getId(), rolId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());

        assertThat(soportePartidoArbitros.existsByPartidoIdAndCuentaId(m.partido().getId(), cuentaIdDe(arbitro))).isFalse();
        mvc.perform(get("/api/supercopa/arbitro/partidos").header("Authorization", arbitro.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── Programación y fixture ──────────────────────────────────────────

    @Test
    void programar_cambiaFechaYLugar_yUnPartidoCerradoNo() throws Exception {
        var m = mundo();
        var admin = cuentaConRoles(Rol.JUGADOR);
        hacerAdmin(admin, m.torneo().getId());
        var nueva = LocalDateTime.now().plusDays(5).withNano(0);

        mvc.perform(put(PARTIDOS + "/{id}/programacion", m.partido().getId())
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fecha", nueva.toString(), "lugar", "  Coliseo  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugar").value("Coliseo"))
                .andExpect(jsonPath("$.fechaHora").value(org.hamcrest.Matchers.startsWith(nueva.toString().substring(0, 16))));

        var cerrado = partido(m, LocalDateTime.now(), EstadoPartido.FINALIZADO);
        mvc.perform(put(PARTIDOS + "/{id}/programacion", cerrado.getId())
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lugar", "Otro"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTIDO_CERRADO"));
    }

    @Test
    void borrarElFixture_conArbitrosAsignados_funciona() throws Exception {
        var m = mundo();
        var admin = cuentaConRoles(Rol.JUGADOR);
        hacerAdmin(admin, m.torneo().getId());
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);
        asignarArbitro(arbitro, m.partido());

        mvc.perform(delete("/api/supercopa/admin/torneos/{id}/fixture", m.torneo().getId()).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
        assertThat(soportePartidos.findByTorneoId(m.torneo().getId())).isEmpty();
    }

    // ── Multas: admin o árbitro del torneo ──────────────────────────────

    @Test
    void multasDelTorneo_lasVeElArbitroDelTorneo_yNadieMas() throws Exception {
        var m = mundo();
        var arbitro = cuentaConRoles(Rol.JUGADOR);
        hacerArbitro(arbitro, m.torneo().getId(), false);

        mvc.perform(get("/api/finanzas/multas/torneo/{id}", m.torneo().getId()).header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finanzas/multas/torneo/{id}", m.torneo().getId())
                        .header("Authorization", cuentaConRoles(Rol.ARBITRO, Rol.JUGADOR).bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ARBITRO_DEL_TORNEO"));
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private record Mundo(Torneo torneo, EquipoTorneo local, EquipoTorneo visitante, Usuario delegadoLocal,
                         UUID jugadorLocal, UUID jugadorVisitante, Partido partido) {
    }

    /** Torneo EN_CURSO con dos equipos (delegado y un jugador cada uno) y un partido de mañana. */
    private Mundo mundo() {
        var torneo = torneoDePrueba(EstadoTorneo.EN_CURSO);
        var delegadoLocal = cuentaConRoles(Rol.JUGADOR);
        var local = inscribir(torneo, delegadoLocal);
        var visitante = inscribir(torneo, cuentaConRoles(Rol.JUGADOR));
        UUID jugadorLocal = miembro(local, "Jugador Local");
        UUID jugadorVisitante = miembro(visitante, "Jugador Visitante");
        var partido = soportePartidos.save(Partido.builder()
                .torneo(torneo).equipoLocalTorneo(local).equipoVisitanteTorneo(visitante)
                .fecha(LocalDateTime.now().plusDays(1)).estado(EstadoPartido.PROGRAMADO)
                .fase(FaseTorneo.GRUPOS).grupo("A").jornada(1)
                .build());
        return new Mundo(torneo, local, visitante, delegadoLocal, jugadorLocal, jugadorVisitante, partido);
    }

    private EquipoTorneo inscribir(Torneo torneo, Usuario delegado) {
        UUID personaId = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, delegado.sub()).orElseThrow()
                .getPersona().getId();
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + UUID.randomUUID().toString().substring(0, 8)).delegadoPersonaId(personaId).build());
        var et = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(personaId)
                .estadoInscripcion(EstadoInscripcion.APROBADO).build());
        jugadorEquipoRepository.save(JugadorEquipo.builder()
                .personaId(personaId).torneo(torneo).equipoTorneo(et)
                .fechaInicio(LocalDate.now()).estado(EstadoMembresia.ACTIVO).numeroCamiseta(1)
                .nombreSnapshot("Delegado").build());
        return et;
    }

    private UUID miembro(EquipoTorneo et, String nombre) {
        var persona = personaRepository.save(terminus.co.edu.ufps.codecup.identidad.model.Persona.builder()
                .nombres(nombre).build());
        jugadorEquipoRepository.save(JugadorEquipo.builder()
                .personaId(persona.getId()).torneo(et.getTorneo()).equipoTorneo(et)
                .fechaInicio(LocalDate.now()).estado(EstadoMembresia.ACTIVO).numeroCamiseta(9)
                .nombreSnapshot(nombre).build());
        return persona.getId();
    }

    private Partido partido(Mundo m, LocalDateTime fecha, EstadoPartido estado) {
        return soportePartidos.save(Partido.builder()
                .torneo(m.torneo()).equipoLocalTorneo(m.local()).equipoVisitanteTorneo(m.visitante())
                .fecha(fecha).estado(estado).fase(FaseTorneo.GRUPOS).jornada(2)
                .build());
    }

    private void programar(Partido p, String lugar) {
        p.setLugar(lugar);
        soportePartidos.save(p);
    }

    private ResultActions evento(Usuario u, Mundo m, UUID personaId, String tipo) throws Exception {
        return mvc.perform(post(PARTIDOS + "/{id}/eventos", m.partido().getId())
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("personaId", personaId, "equipoTorneoId", equipoDe(m, personaId).getId(),
                        "tipoEvento", tipo))));
    }

    private ResultActions alinear(Usuario u, Mundo m, UUID personaId) throws Exception {
        return alinear(u, m, personaId, equipoDe(m, personaId));
    }

    private ResultActions alinear(Usuario u, Mundo m, UUID personaId, EquipoTorneo et) throws Exception {
        return mvc.perform(post(PARTIDOS + "/{id}/alineacion", m.partido().getId())
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("personaId", personaId, "equipoTorneoId", et.getId()))));
    }

    private ResultActions asignar(Usuario quien, Partido p, Usuario arbitro) throws Exception {
        return mvc.perform(post(PARTIDOS + "/{id}/arbitros", p.getId())
                .header("Authorization", quien.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("cuentaId", cuentaIdDe(arbitro)))));
    }

    private static EquipoTorneo equipoDe(Mundo m, UUID personaId) {
        return personaId.equals(m.jugadorVisitante()) ? m.visitante() : m.local();
    }
}

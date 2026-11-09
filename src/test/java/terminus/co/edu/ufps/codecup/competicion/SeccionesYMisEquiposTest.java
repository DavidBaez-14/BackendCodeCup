package terminus.co.edu.ufps.codecup.competicion;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;

/**
 * Spec 0026: "Tus secciones" en {@code GET /api/auth/me} y "Mis equipos" en
 * {@code GET /api/supercopa/yo/equipos}, para que Inicio se pinte sin pedir todo.
 */
class SeccionesYMisEquiposTest extends AuthTestSupport {

    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired JugadorEquipoRepository jugadorEquipoRepository;
    @Autowired ComprobanteRepository comprobanteRepository;
    @Autowired Clock reloj;

    // ── CA-5: secciones ─────────────────────────────────────────────────

    @Test
    void ca5_cuentaNueva_sinSecciones_niEquipos() throws Exception {
        var u = registrado();
        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secciones.creador").value(nullValue()))
                .andExpect(jsonPath("$.secciones.arbitro").value(nullValue()));
        mvc.perform(get("/api/supercopa/yo/equipos").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ca5_creador_cuentaSusTorneosYLoPorRevisar() throws Exception {
        var u = registrado();
        String creado = mvc.perform(post("/api/supercopa/admin/torneos").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nombre", "Copa Inicio", "edicion", 2026))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Torneo torneo = soporteTorneos.findById(UUID.fromString(objectMapper.readTree(creado).get("id").asText())).orElseThrow();
        torneo.setEstado(EstadoTorneo.PUBLICADO);
        soporteTorneos.save(torneo);
        // Dos inscripciones pendientes; una con su comprobante (no se cuenta dos veces).
        var una = inscripcion(torneo, cuentaConRoles(), EstadoInscripcion.PENDIENTE_PAGO);
        inscripcion(torneo, cuentaConRoles(), EstadoInscripcion.PENDIENTE_PAGO);
        comprobante(una);

        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.secciones.creador.torneos").value(1))
                .andExpect(jsonPath("$.secciones.creador.porRevisar").value(2))
                .andExpect(jsonPath("$.secciones.arbitro").value(nullValue()));
    }

    @Test
    void ca5_arbitro_conElPartidoDeHoy_ySinPartidosTambienSeVe() throws Exception {
        var torneo = torneoDePrueba(EstadoTorneo.EN_CURSO);
        var local = inscripcion(torneo, cuentaConRoles(), EstadoInscripcion.APROBADO);
        var visitante = inscripcion(torneo, cuentaConRoles(), EstadoInscripcion.APROBADO);
        var hoy = soportePartidos.save(Partido.builder().torneo(torneo)
                .equipoLocalTorneo(local).equipoVisitanteTorneo(visitante)
                .fecha(LocalDate.now(reloj).atTime(12, 0)).lugar("Cancha 3").estado(EstadoPartido.PROGRAMADO).build());
        var manana = soportePartidos.save(Partido.builder().torneo(torneo)
                .equipoLocalTorneo(visitante).equipoVisitanteTorneo(local)
                .fecha(LocalDate.now(reloj).plusDays(1).atTime(12, 0)).estado(EstadoPartido.PROGRAMADO).build());

        var arbitro = registrado();
        hacerArbitro(arbitro, torneo.getId(), false);
        mvc.perform(get("/api/auth/me").header("Authorization", arbitro.bearer()))
                .andExpect(jsonPath("$.secciones.arbitro.partidosAsignados").value(0))
                .andExpect(jsonPath("$.secciones.arbitro.hoy").value(nullValue()));

        asignarArbitro(arbitro, hoy);
        asignarArbitro(arbitro, manana);
        mvc.perform(get("/api/auth/me").header("Authorization", arbitro.bearer()))
                .andExpect(jsonPath("$.secciones.arbitro.partidosAsignados").value(2))
                .andExpect(jsonPath("$.secciones.arbitro.hoy.partidoId").value(hoy.getId().toString()))
                .andExpect(jsonPath("$.secciones.arbitro.hoy.local").value(local.getEquipo().getNombre()))
                .andExpect(jsonPath("$.secciones.arbitro.hoy.lugar").value("Cancha 3"))
                .andExpect(jsonPath("$.secciones.creador").value(nullValue()));
    }

    // ── Mis equipos ─────────────────────────────────────────────────────

    @Test
    void misEquipos_delegaUnoYJuegaEnOtro_conTramiteYProximoPartido() throws Exception {
        var u = registrado();
        UUID persona = personaDe(u);
        var torneoA = torneoDePrueba(EstadoTorneo.EN_CURSO);
        var torneoB = torneoDePrueba(EstadoTorneo.PUBLICADO);

        // En A delega (y por eso juega), con comprobante y un partido pasado mañana.
        var delega = inscripcionDe(torneoA, persona, "Delego FC", EstadoInscripcion.APROBADO);
        comprobante(delega);
        var rival = inscripcion(torneoA, cuentaConRoles(), EstadoInscripcion.APROBADO);
        soportePartidos.save(Partido.builder().torneo(torneoA).equipoLocalTorneo(rival).equipoVisitanteTorneo(delega)
                .fecha(LocalDateTime.now().plusDays(2)).lugar("Coliseo").estado(EstadoPartido.PROGRAMADO).build());
        soportePartidos.save(Partido.builder().torneo(torneoA).equipoLocalTorneo(delega).equipoVisitanteTorneo(rival)
                .fecha(LocalDateTime.now().minusDays(3)).estado(EstadoPartido.FINALIZADO).build());

        // En B solo juega, en el equipo de otro.
        var ajeno = inscripcion(torneoB, cuentaConRoles(), EstadoInscripcion.PENDIENTE_PAGO);
        miembro(ajeno, persona, 7);

        mvc.perform(get("/api/supercopa/yo/equipos").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].equipoTorneoId").value(delega.getId().toString()))
                .andExpect(jsonPath("$[0].rol").value("DELEGADO"))
                .andExpect(jsonPath("$[0].estadoInscripcion").value("APROBADO"))
                .andExpect(jsonPath("$[0].comprobante").value("PENDIENTE_REVISION"))
                .andExpect(jsonPath("$[0].proximoPartido.rival").value(rival.getEquipo().getNombre()))
                .andExpect(jsonPath("$[0].proximoPartido.local").value(false))
                .andExpect(jsonPath("$[0].proximoPartido.lugar").value("Coliseo"))
                .andExpect(jsonPath("$[1].equipoTorneoId").value(ajeno.getId().toString()))
                .andExpect(jsonPath("$[1].rol").value("JUGADOR"))
                .andExpect(jsonPath("$[1].estadoInscripcion").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$[1].comprobante").value(nullValue()))
                .andExpect(jsonPath("$[1].numeroCamiseta").value(7))
                .andExpect(jsonPath("$[1].proximoPartido").value(nullValue()))
                .andExpect(jsonPath("$[*].torneoId").value(contains(torneoA.getId().toString(), torneoB.getId().toString())));
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

    private EquipoTorneo inscripcion(Torneo torneo, Usuario delegado, EstadoInscripcion estado) {
        return inscripcionDe(torneo, personaDe(delegado), "Equipo " + UUID.randomUUID().toString().substring(0, 6), estado);
    }

    private EquipoTorneo inscripcionDe(Torneo torneo, UUID persona, String nombre, EstadoInscripcion estado) {
        var equipo = equipoRepository.save(Equipo.builder().nombre(nombre).delegadoPersonaId(persona).build());
        var et = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(persona).estadoInscripcion(estado).build());
        miembro(et, persona, 1);
        return et;
    }

    private void miembro(EquipoTorneo et, UUID persona, int camiseta) {
        jugadorEquipoRepository.save(JugadorEquipo.builder()
                .personaId(persona).torneo(et.getTorneo()).equipoTorneo(et)
                .fechaInicio(LocalDate.now()).estado(EstadoMembresia.ACTIVO).numeroCamiseta(camiseta).build());
    }

    private void comprobante(EquipoTorneo et) {
        comprobanteRepository.save(Comprobante.builder()
                .tipo(TipoComprobante.INSCRIPCION).referenciaId(et.getId()).urlArchivo(et.getId() + "/comprobante")
                .subidoPorCuentaId(cuentaIdDe(cuentaConRoles())).estado(EstadoComprobante.PENDIENTE_REVISION)
                .fechaSubida(LocalDateTime.now()).build());
    }
}

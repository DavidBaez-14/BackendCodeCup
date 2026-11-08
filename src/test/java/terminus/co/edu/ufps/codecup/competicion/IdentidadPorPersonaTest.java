package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.MultaConfig;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.MultaConfigRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0022, criterios 10, 12 y 16: partido, multas y equipos del torneo funcionan por persona.
 * Un miembro sin cuenta juega, recibe una amarilla, la multa sale a su nombre al cerrar el partido
 * y el pago queda registrado con la cuenta del árbitro.
 */
class IdentidadPorPersonaTest extends AuthTestSupport {

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired PartidoRepository partidoRepository;
    @Autowired MultaConfigRepository multaConfigRepository;
    @Autowired MultaRepository multaRepository;

    @Test
    void miembroSinCuenta_juegaRecibeMulta_yElPagoGuardaLaCuentaDelArbitro() throws Exception {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Liga " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.EN_CURSO)
                .build());
        multaConfigRepository.save(MultaConfig.builder()
                .torneoId(torneo.getId())
                .montoAmarilla(new BigDecimal("5000")).montoAzul(new BigDecimal("8000")).montoRoja(new BigDecimal("15000"))
                .build());
        var a = inscribir(torneo);
        var b = inscribir(torneo);

        String doc = documentoAlAzar();
        String agregado = mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros", a.inscripcion().getId())
                        .header("Authorization", a.delegado().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("numeroDocumento", doc, "nombre", "Beto Sin Cuenta"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String beto = objectMapper.readTree(agregado).get("personaId").asText();

        var partido = partidoRepository.save(Partido.builder()
                .torneo(torneo)
                .equipoLocalTorneo(a.inscripcion())
                .equipoVisitanteTorneo(b.inscripcion())
                .fecha(LocalDateTime.now())
                .estado(EstadoPartido.PROGRAMADO)
                .build());
        Usuario arbitro = cuentaConRoles(Rol.ARBITRO);
        UUID cuentaArbitro = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, arbitro.sub()).orElseThrow().getId();

        // Alineación por persona: entra, sale y vuelve a entrar.
        var alineacion = Map.of("personaId", beto, "equipoTorneoId", a.inscripcion().getId());
        mvc.perform(post("/api/supercopa/admin/partidos/{id}/alineacion", partido.getId())
                        .header("Authorization", arbitro.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(alineacion)))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(delete("/api/supercopa/admin/partidos/{id}/alineacion/{personaId}", partido.getId(), beto)
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/supercopa/admin/partidos/{id}/alineacion", partido.getId())
                        .header("Authorization", arbitro.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(alineacion)))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/supercopa/admin/partidos/{id}/alineacion", partido.getId())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.personaId == '%s')].numeroDocumento", beto).value(contains(doc)))
                .andExpect(jsonPath("$[?(@.personaId == '%s')].jugadorNombre", beto).value(contains("Beto Sin Cuenta")));

        // Evento por persona.
        mvc.perform(post("/api/supercopa/admin/partidos/{id}/eventos", partido.getId())
                        .header("Authorization", arbitro.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaId", beto, "equipoTorneoId", a.inscripcion().getId(),
                                "tipoEvento", "AMARILLA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaId").value(beto))
                .andExpect(jsonPath("$.jugadorNombre").value("Beto Sin Cuenta"));

        // Al cerrar, la multa sale a nombre de esa persona.
        mvc.perform(post("/api/supercopa/admin/partidos/{id}/cerrar", partido.getId())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk());
        String multas = mvc.perform(get("/api/finanzas/multas/torneo/{id}", torneo.getId())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personaId").value(beto))
                .andExpect(jsonPath("$[0].jugadorNombre").value("Beto Sin Cuenta"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        String multaId = objectMapper.readTree(multas).get(0).get("id").asText();

        mvc.perform(get("/api/finanzas/multas/elegibilidad/{personaId}/detalle", beto)
                        .param("torneoId", torneo.getId().toString())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apto").value(false))
                .andExpect(jsonPath("$.motivos").value(hasItem("MULTA_PENDIENTE")));

        // El pago registra la cuenta de quien lo cobró y muestra su nombre.
        mvc.perform(post("/api/finanzas/multas/{id}/registrar-pago", multaId)
                        .header("Authorization", arbitro.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("partidoPagoId", partido.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.pagadoPor").value("Admin Prueba"))
                .andExpect(jsonPath("$.pagadoPorCuentaId").value(cuentaArbitro.toString()));
        assertThat(multaRepository.findById(UUID.fromString(multaId)).orElseThrow().getPagadoPorCuentaId())
                .isEqualTo(cuentaArbitro);

        mvc.perform(get("/api/finanzas/multas/elegibilidad/{personaId}/detalle", beto)
                        .param("torneoId", torneo.getId().toString())
                        .header("Authorization", arbitro.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apto").value(true));
    }

    @Test
    void equiposDelTorneo_traenElNombreDelDelegado_yNoSuCedula() throws Exception {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
        inscribir(torneo);
        inscribir(torneo);
        var jugador = usuario();
        registrar(jugador);

        String respuesta = mvc.perform(get("/api/supercopa/jugador/torneos/{id}/equipos", torneo.getId())
                        .header("Authorization", jugador.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].delegadoNombre", everyItem(org.hamcrest.Matchers.is("Admin Prueba"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("delegadoCedula").doesNotContain("numeroDocumento");
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private record Inscrito(Usuario delegado, EquipoTorneo inscripcion) {
    }

    private Inscrito inscribir(Torneo torneo) {
        Usuario delegado = cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR);
        UUID delegadoId = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, delegado.sub())
                .orElseThrow().getPersona().getId();
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + delegadoId.toString().substring(0, 8))
                .delegadoPersonaId(delegadoId)
                .build());
        var inscripcion = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(delegadoId)
                .estadoInscripcion(EstadoInscripcion.APROBADO)
                .build());
        return new Inscrito(delegado, inscripcion);
    }
}

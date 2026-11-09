package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.Visibilidad;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;

/**
 * Spec 0029: deportes, visibilidad (PUBLICO, NO_LISTADO y PRIVADO), crear un torneo en un paso y
 * las lecturas públicas de Explorar y del detalle, con un solo {@code VisibilidadService}.
 */
class VisibilidadYPublicoTest extends AuthTestSupport {

    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired Clock reloj;

    @BeforeEach
    void catalogo() {
        asegurarDeportes();
    }

    // ── Catálogo ────────────────────────────────────────────────────────

    @Test
    void deportes_sinSesion_enOrden_conSuTipo() throws Exception {
        mvc.perform(get("/api/public/deportes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo").value(contains("FUTSAL", "VOLEIBOL", "BALONCESTO", "TENIS_MESA")))
                .andExpect(jsonPath("$[3].tipoCompetencia").value("INDIVIDUAL"));
    }

    // ── Crear en un paso ────────────────────────────────────────────────

    @Test
    void crearRapido_naceConInscripcionesAbiertas_formatoPorDefecto_yEnlace() throws Exception {
        var u = registrado();
        String respuesta = crearRapido(u, cuerpo(Map.of("formato", "ELIMINACION_DIRECTA", "cupos", 8,
                        "cobraInscripcion", true, "valorInscripcion", 5000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.deporte").value("TENIS_MESA"))
                .andExpect(jsonPath("$.visibilidad").value("PUBLICO"))
                .andExpect(jsonPath("$.valorInscripcion").value(5000))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(respuesta).get("id").asText();
        String token = objectMapper.readTree(respuesta).get("enlaceToken").asText();
        assertThat(objectMapper.readTree(respuesta).get("enlace").asText()).endsWith("/t/" + token);

        mvc.perform(get("/api/supercopa/admin/torneos/{id}/configuracion", id).header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formato").value("ELIMINACION_DIRECTA"))
                .andExpect(jsonPath("$.rondasPlayoff").value(contains("CUARTOS", "SEMIS", "FINAL")));
        mvc.perform(get("/api/supercopa/creador/torneos").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$[0].deporte").value("TENIS_MESA"))
                .andExpect(jsonPath("$[0].cupos").value(8))
                .andExpect(jsonPath("$[0].enlace").value(endsWith(token)));
    }

    @Test
    void crearRapido_cadaCampoInvalido_tieneSuCodigo() throws Exception {
        var u = registrado();
        Object[][] casos = {
            {"nombre", "ab", "NOMBRE_INVALIDO"},
            {"deporte", "AJEDREZ", "DEPORTE_INEXISTENTE"},
            {"formato", "CHAMPIONS", "FORMATO_INVALIDO"},
            {"cupos", 32, "CUPOS_FUERA_DE_RANGO"}, // eliminación directa: hasta 16
            {"fechaInicio", LocalDate.now(reloj).minusDays(1).toString(), "FECHA_INICIO_PASADA"},
            {"visibilidad", "SECRETO", "VISIBILIDAD_INVALIDA"},
            {"valorInscripcion", 0, "VALOR_INSCRIPCION_INVALIDO"},
        };
        for (Object[] caso : casos) {
            var cuerpo = cuerpo(Map.of("formato", "ELIMINACION_DIRECTA", "cupos", 8, "cobraInscripcion", true,
                    "valorInscripcion", 1000));
            cuerpo.put((String) caso[0], caso[1]);
            crearRapido(u, cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(caso[2]))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
        var sinFecha = cuerpo(Map.of());
        sinFecha.remove("fechaInicio");
        crearRapido(u, sinFecha).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FECHA_INICIO_REQUERIDA"));
    }

    // ── Explorar ────────────────────────────────────────────────────────

    @Test
    void explorar_soloPublicos_sinBorradores_yBuscaSinTildes() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        var publico = torneo("Copa Ñandú Ágil " + sufijo, Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "FUTSAL");
        torneo("Copa Ñandú Oculta " + sufijo, Visibilidad.NO_LISTADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        torneo("Copa Ñandú Privada " + sufijo, Visibilidad.PRIVADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        torneo("Copa Ñandú Borrador " + sufijo, Visibilidad.PUBLICO, EstadoTorneo.BORRADOR, "FUTSAL");
        torneo("Copa Ñandú Voley " + sufijo, Visibilidad.PUBLICO, EstadoTorneo.EN_CURSO, "VOLEIBOL");

        mvc.perform(get("/api/public/torneos").param("q", "nandu " + sufijo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(publico.getId().toString()))
                .andExpect(jsonPath("$.contenido[0].inscripcionesAbiertas").value(true))
                .andExpect(jsonPath("$.contenido[0].deporteNombre").value("Fútbol sala"))
                .andExpect(jsonPath("$.contenido[1].estado").value("EN_CURSO"));
        mvc.perform(get("/api/public/torneos").param("q", "ÑANDÚ ÁGIL " + sufijo))
                .andExpect(jsonPath("$.contenido[*].id").value(contains(publico.getId().toString())));
        mvc.perform(get("/api/public/torneos").param("q", sufijo).param("deporte", "VOLEIBOL"))
                .andExpect(jsonPath("$.contenido", hasSize(1)));
        mvc.perform(get("/api/public/torneos").param("q", sufijo).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    // ── Detalle por id y por enlace ─────────────────────────────────────

    @Test
    void detalle_publicoSinSesion_sinDatosPersonales_yNoListadoSoloConEnlace() throws Exception {
        var publico = torneo("Pública " + UUID.randomUUID(), Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var deUno = inscripcion(publico, registrado(), EstadoInscripcion.APROBADO);
        String cuerpo = mvc.perform(get("/api/public/torneos/{id}", publico.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deporte.nombre").value("Fútbol sala"))
                .andExpect(jsonPath("$.equipos[*].equipoTorneoId").value(contains(deUno.getId().toString())))
                .andExpect(jsonPath("$.miRelacion").value(nullValue()))
                .andExpect(jsonPath("$.enlace").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("numeroDocumento").doesNotContain("correo").doesNotContain("@");

        var oculto = torneo("Oculta " + UUID.randomUUID(), Visibilidad.NO_LISTADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        mvc.perform(get("/api/public/torneos/{id}", oculto.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ENCONTRADO"));
        mvc.perform(get("/api/public/t/{token}", oculto.getEnlaceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(oculto.getId().toString()));
        mvc.perform(get("/api/public/t/{token}", "no-existe")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/torneos/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void detalle_privado_soloParticipantes_conMiRelacion() throws Exception {
        var privado = torneo("Privada " + UUID.randomUUID(), Visibilidad.PRIVADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var admin = registrado();
        hacerAdmin(admin, privado.getId());
        var delegado = registrado();
        var et = inscripcion(privado, delegado, EstadoInscripcion.PENDIENTE_PAGO);
        var arbitro = registrado();
        hacerArbitro(arbitro, privado.getId(), false);

        mvc.perform(get("/api/public/t/{token}", privado.getEnlaceToken())).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/torneos/{id}", privado.getId()).header("Authorization", registrado().bearer()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/public/torneos/{id}", privado.getId()).header("Authorization", delegado.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miRelacion.administra").value(false))
                .andExpect(jsonPath("$.miRelacion.delegadoDe.equipoTorneoId").value(et.getId().toString()))
                .andExpect(jsonPath("$.miRelacion.delegadoDe.estadoInscripcion").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.miRelacion.juegaEn.equipoTorneoId").value(et.getId().toString()))
                .andExpect(jsonPath("$.enlace").value(nullValue()));
        mvc.perform(get("/api/public/torneos/{id}", privado.getId()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.miRelacion.administra").value(true))
                .andExpect(jsonPath("$.enlace").isNotEmpty());
        mvc.perform(get("/api/public/torneos/{id}", privado.getId()).header("Authorization", arbitro.bearer()))
                .andExpect(jsonPath("$.miRelacion.arbitra").value(true))
                .andExpect(jsonPath("$.miRelacion.delegadoDe").value(nullValue()));
        // Los analytics por id tampoco lo muestran a quien no participa.
        mvc.perform(get("/api/analytics/torneos/{id}/posiciones", privado.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/analytics/torneos/{id}/posiciones", privado.getId()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
    }

    // ── Listas con sesión ───────────────────────────────────────────────

    @Test
    void listasConSesion_noMuestranNoListadosNiPrivadosAjenos() throws Exception {
        var oculto = torneo("Oculta " + UUID.randomUUID(), Visibilidad.NO_LISTADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var privado = torneo("Privada " + UUID.randomUUID(), Visibilidad.PRIVADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var publico = torneo("Pública " + UUID.randomUUID(), Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var u = registrado();

        for (String ruta : new String[] {"/api/supercopa/torneos", "/api/supercopa/delegado/torneos/disponibles"}) {
            mvc.perform(get(ruta).header("Authorization", u.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(hasItem(publico.getId().toString())))
                    .andExpect(jsonPath("$[*].id").value(not(hasItem(oculto.getId().toString()))))
                    .andExpect(jsonPath("$[*].id").value(not(hasItem(privado.getId().toString()))));
        }
    }

    // ── Inscribirse respeta visibilidad, deporte, cupos y cierre ─────────

    @Test
    void inscribir_noListadoConEnlace_privadoNunca_individualNo() throws Exception {
        var oculto = torneo("Oculta " + UUID.randomUUID(), Visibilidad.NO_LISTADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var u = registrado();
        inscribir(u, oculto, Map.of("nombreEquipo", "Sin Enlace")).andExpect(status().isNotFound());
        inscribir(u, oculto, Map.of("nombreEquipo", "Con Enlace", "enlaceToken", oculto.getEnlaceToken()))
                .andExpect(status().isOk());

        var privado = torneo("Privada " + UUID.randomUUID(), Visibilidad.PRIVADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        inscribir(registrado(), privado, Map.of("nombreEquipo", "Intruso", "enlaceToken", privado.getEnlaceToken()))
                .andExpect(status().isNotFound());

        var individual = torneo("Tenis " + UUID.randomUUID(), Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "TENIS_MESA");
        inscribir(registrado(), individual, Map.of("nombreEquipo", "Dobles"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TORNEO_INDIVIDUAL"));
    }

    @Test
    void cupos_seRespetanAlInscribirYAlAprobar_yElCierreCierra() throws Exception {
        var t = torneo("Cupos " + UUID.randomUUID(), Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "FUTSAL");
        t.setCupos(2);
        soporteTorneos.save(t);
        var admin = registrado();
        hacerAdmin(admin, t.getId());
        inscripcion(t, registrado(), EstadoInscripcion.APROBADO);
        var pendiente = inscripcion(t, registrado(), EstadoInscripcion.PENDIENTE_PAGO);
        inscripcion(t, registrado(), EstadoInscripcion.APROBADO);

        inscribir(registrado(), t, Map.of("nombreEquipo", "Tarde")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUPOS_AGOTADOS"));
        mvc.perform(post("/api/supercopa/admin/torneos/{t}/inscripciones/{i}/aprobar", t.getId(), pendiente.getId())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUPOS_AGOTADOS"));
        mvc.perform(get("/api/public/torneos/{id}", t.getId()))
                .andExpect(jsonPath("$.inscritos").value(2))
                .andExpect(jsonPath("$.inscripcionesAbiertas").value(false));

        var cerrado = torneo("Cerrado " + UUID.randomUUID(), Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "FUTSAL");
        cerrado.setCierreInscripciones(LocalDate.now(reloj).minusDays(1));
        soporteTorneos.save(cerrado);
        inscribir(registrado(), cerrado, Map.of("nombreEquipo", "Tarde")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSCRIPCIONES_CERRADAS"));
    }

    // ── Editar datos y enlace ───────────────────────────────────────────

    @Test
    void datos_cambiarVisibilidad_cupos_yDeporte_conSusLimites() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        var t = torneo("Editable " + sufijo, Visibilidad.PUBLICO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var admin = registrado();
        hacerAdmin(admin, t.getId());
        inscripcion(t, registrado(), EstadoInscripcion.APROBADO);
        inscripcion(t, registrado(), EstadoInscripcion.APROBADO);
        inscripcion(t, registrado(), EstadoInscripcion.APROBADO);

        datos(admin, t, Map.of("visibilidad", "NO_LISTADO", "reglas", "  Partidos de 2 tiempos  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibilidad").value("NO_LISTADO"))
                .andExpect(jsonPath("$.reglas").value("Partidos de 2 tiempos"));
        mvc.perform(get("/api/public/torneos").param("q", sufijo)).andExpect(jsonPath("$.totalElementos").value(0));

        datos(admin, t, Map.of("cupos", 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUPOS_MENOR_QUE_INSCRITOS"));
        datos(admin, t, Map.of("deporte", "VOLEIBOL")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPORTE_BLOQUEADO"));
        datos(registrado(), t, Map.of("visibilidad", "PUBLICO")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ADMIN_DEL_TORNEO"));
    }

    @Test
    void regenerarEnlace_elAnteriorDejaDeFuncionar() throws Exception {
        var t = torneo("Enlace " + UUID.randomUUID(), Visibilidad.NO_LISTADO, EstadoTorneo.PUBLICADO, "FUTSAL");
        var admin = registrado();
        hacerAdmin(admin, t.getId());
        String anterior = t.getEnlaceToken();

        String nuevo = objectMapper.readTree(mvc.perform(post("/api/supercopa/admin/torneos/{id}/enlace/regenerar", t.getId())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("enlaceToken").asText();

        assertThat(nuevo).isNotEqualTo(anterior);
        mvc.perform(get("/api/public/t/{token}", anterior)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/t/{token}", nuevo)).andExpect(status().isOk());
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private Usuario registrado() throws Exception {
        var u = usuario();
        registrar(u);
        return u;
    }

    private Map<String, Object> cuerpo(Map<String, Object> extra) {
        var cuerpo = new HashMap<String, Object>(Map.of(
                "nombre", "Relámpago de Tenis de Mesa", "deporte", "TENIS_MESA", "formato", "LIGA", "cupos", 16,
                "fechaInicio", LocalDate.now(reloj).plusDays(7).toString(), "visibilidad", "PUBLICO",
                "cobraInscripcion", false));
        cuerpo.putAll(extra);
        return cuerpo;
    }

    private ResultActions crearRapido(Usuario u, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/supercopa/creador/torneos").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private ResultActions inscribir(Usuario u, Torneo t, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/supercopa/delegado/torneos/{id}/inscribir", t.getId()).header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private ResultActions datos(Usuario u, Torneo t, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(put("/api/supercopa/admin/torneos/{id}/datos", t.getId()).header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    private Torneo torneo(String nombre, Visibilidad visibilidad, EstadoTorneo estado, String deporte) {
        return soporteTorneos.save(Torneo.builder().nombre(nombre).edicion(2026).estado(estado)
                .visibilidad(visibilidad).deporte(deporte).build());
    }

    private EquipoTorneo inscripcion(Torneo t, Usuario delegado, EstadoInscripcion estado) {
        UUID persona = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, delegado.sub()).orElseThrow()
                .getPersona().getId();
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + UUID.randomUUID().toString().substring(0, 6)).delegadoPersonaId(persona).build());
        var et = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(t).equipo(equipo).delegadoPersonaId(persona).estadoInscripcion(estado).build());
        soporteJugadores(et, persona);
        return et;
    }

    @Autowired terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository jugadorEquipoRepository;

    private void soporteJugadores(EquipoTorneo et, UUID persona) {
        jugadorEquipoRepository.save(terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo.builder()
                .personaId(persona).torneo(et.getTorneo()).equipoTorneo(et).fechaInicio(LocalDate.now())
                .estado(terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia.ACTIVO).build());
    }
}

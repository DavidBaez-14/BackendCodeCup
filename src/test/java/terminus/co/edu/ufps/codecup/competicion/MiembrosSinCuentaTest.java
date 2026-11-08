package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoSolicitud;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EventoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.codecup.competicion.model.SolicitudEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.SolicitudEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/**
 * Spec 0018 (adaptada a la 0022): el delegado agrega miembros sin cuenta, el documento se
 * normaliza, una cuenta bloqueada no entra a ningún plantel y el nombre registrado gana al
 * mostrar. Desde la 0022 un miembro sin cuenta es una {@code identidad.personas} sin cuenta, y
 * quien se registra después con su documento queda asociado a esa misma persona.
 *
 * <p>El H2 se comparte entre clases: cada test arma su propio torneo con documentos al azar.
 */
class MiembrosSinCuentaTest extends AuthTestSupport {

    private static final String NOMBRE_REGISTRADO = "Ana María Pérez"; // el que usa AuthTestSupport.registrar

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired JugadorEquipoRepository jugadorEquipoRepository;
    @Autowired PartidoRepository partidoRepository;
    @Autowired PartidoJugadorRepository partidoJugadorRepository;
    @Autowired EventoPartidoRepository eventoPartidoRepository;
    @Autowired SolicitudEquipoRepository solicitudEquipoRepository;

    // ── Criterios 1 a 7: agregar miembros ───────────────────────────────

    @Test
    void sinCuenta_conNombre_creaPersonaSinCuentaYSnapshot() throws Exception {
        var m = mundo();
        String doc = documentoAlAzar();

        agregar(m, cuerpo(doc, "  Ana Pérez  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").value(doc))
                .andExpect(jsonPath("$.personaId").isNotEmpty())
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.tieneCuenta").value(false))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        Persona persona = persona(doc);
        assertThat(persona.getNombres()).isEqualTo("Ana Pérez");
        assertThat(persona.getTipoDocumento()).isEqualTo(TipoDocumento.CC);
        assertThat(cuentaRepository.findByPersonaId(persona.getId())).isEmpty();
        var je = jugadorEquipoRepository.findByPersonaIdAndTorneoId(persona.getId(), m.torneo().getId()).orElseThrow();
        assertThat(je.getNombreSnapshot()).isEqualTo("Ana Pérez");
        assertThat(je.getEstado()).isEqualTo(EstadoMembresia.ACTIVO);
    }

    @Test
    void sinCuenta_conTipoDeDocumento_loGuarda() throws Exception {
        var m = mundo();
        String doc = documentoAlAzar();
        var c = cuerpo(doc, "Tomás Niño");
        c.put("tipoDocumento", "TI");

        agregar(m, c).andExpect(status().isOk());

        assertThat(persona(doc).getTipoDocumento()).isEqualTo(TipoDocumento.TI);
    }

    @Test
    void sinCuenta_sinNombre_400NombreRequerido_yNoGuarda() throws Exception {
        var m = mundo();
        String doc = documentoAlAzar();

        agregar(m, cuerpo(doc, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_REQUERIDO"));
        agregar(m, cuerpo(doc, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_REQUERIDO"));

        assertThat(personaRepository.existsByNumeroDocumento(doc)).isFalse();
    }

    @Test
    void registrado_usaNombreDeLaPersona_aunqueEnvieOtro() throws Exception {
        var m = mundo();
        var r = registrado();

        agregar(m, cuerpo(r.documento(), "Anita"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(NOMBRE_REGISTRADO))
                .andExpect(jsonPath("$.tieneCuenta").value(true));

        assertThat(jugadorEquipoRepository.findByPersonaIdAndTorneoId(persona(r.documento()).getId(), m.torneo().getId())
                .orElseThrow().getNombreSnapshot()).isEqualTo(NOMBRE_REGISTRADO);
    }

    @Test
    void personaSinCuentaExistente_nombreOpcional_yNoSeRenombra() throws Exception {
        String doc = documentoAlAzar();
        agregar(mundo(), cuerpo(doc, "Juan Pérez")).andExpect(status().isOk());

        // Otro torneo, sin nombre: toma el que ya estaba guardado.
        agregar(mundo(), cuerpo(doc, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Pérez"))
                .andExpect(jsonPath("$.tieneCuenta").value(false));

        // Otro torneo, con nombre: solo cambia el snapshot de ese torneo.
        var tercero = mundo();
        agregar(tercero, cuerpo(doc, "Juancho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juancho"));

        var persona = persona(doc);
        assertThat(jugadorEquipoRepository.findByPersonaIdAndTorneoId(persona.getId(), tercero.torneo().getId())
                .orElseThrow().getNombreSnapshot()).isEqualTo("Juancho");
        assertThat(persona.getNombres()).isEqualTo("Juan Pérez");
    }

    @Test
    void documentoConPuntosYEspacios_reconoceALaPersona_sinDuplicar() throws Exception {
        var m = mundo();
        var r = registrado();
        String escrito = conPuntosYEspacios(r.documento());

        agregar(m, cuerpo(escrito, "Otro nombre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").value(r.documento()))
                .andExpect(jsonPath("$.nombre").value(NOMBRE_REGISTRADO))
                .andExpect(jsonPath("$.tieneCuenta").value(true));

        assertThat(personaRepository.findFirstByNumeroDocumento(escrito)).isEmpty();
        assertThat(jugadorEquipoRepository.findByPersonaIdAndTorneoId(persona(r.documento()).getId(), m.torneo().getId()))
                .isPresent();
    }

    @Test
    void pasaporteEnMinusculas_seGuardaEnMayusculas() throws Exception {
        var m = mundo();
        String pasaporte = "ab" + ThreadLocalRandom.current().nextInt(100_000, 999_999);

        agregar(m, cuerpo(pasaporte, "John Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").value(pasaporte.toUpperCase()));

        assertThat(personaRepository.findFirstByNumeroDocumento(pasaporte.toUpperCase())).isPresent();
    }

    @Test
    void cuerpoInvalido_400Validacion() throws Exception {
        var m = mundo();

        agregar(m, cuerpo(null, "Ana"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        agregar(m, cuerpo(" . . ", "Ana"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        agregar(m, cuerpo("1".repeat(21), "Ana"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        agregar(m, cuerpo(documentoAlAzar(), "a".repeat(151)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
    }

    @Test
    void nombreDe150Caracteres_cabeEnLaPersona() throws Exception {
        var m = mundo();
        String doc = documentoAlAzar();

        agregar(m, cuerpo(doc, "N".repeat(150))).andExpect(status().isOk());

        assertThat(persona(doc).getNombres()).hasSize(150);
    }

    @Test
    void reglasDeHoy_valenParaSinCuenta() throws Exception {
        var a = mundo();
        String x = documentoAlAzar();
        agregar(a, Map.of("numeroDocumento", x, "nombre", "Ximena", "numeroCamiseta", 10)).andExpect(status().isOk());
        UUID ximena = persona(x).getId();

        // No puede estar ACTIVO en dos equipos del mismo torneo.
        var b = otroEquipoEnElMismoTorneo(a);
        agregar(b, cuerpo(x, "Ximena"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGLA_NEGOCIO"));

        // La camiseta 10 ya es de Ximena en este equipo.
        agregar(a, Map.of("numeroDocumento", documentoAlAzar(), "nombre", "Yolanda", "numeroCamiseta", 10))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGLA_NEGOCIO"));

        // Un delegado no agrega en la inscripción de otro.
        mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros", a.inscripcion().getId())
                        .header("Authorization", b.delegado().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo(documentoAlAzar(), "Zoe"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGLA_NEGOCIO"));

        // Se retira, vuelve (con el nombre guardado) y cambia de camiseta.
        mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros/{personaId}/remover",
                        a.inscripcion().getId(), ximena)
                        .header("Authorization", a.delegado().bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RETIRADO"));
        agregar(a, cuerpo(x, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.nombre").value("Ximena"));
        mvc.perform(patch("/api/supercopa/delegado/equipo-torneo/{id}/miembros/{personaId}/camiseta",
                        a.inscripcion().getId(), ximena)
                        .header("Authorization", a.delegado().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("numeroCamiseta", 7))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numeroCamiseta").value(7));
    }

    // ── Criterio 8: cuentas bloqueadas ──────────────────────────────────

    @Test
    void cuentaBloqueada_noEntraAlPlantel_yConservaSuHistorial() throws Exception {
        var a = mundo();
        var r = registrado();
        agregar(a, cuerpo(r.documento(), null)).andExpect(status().isOk());

        var admin = cuentaConRoles(Rol.ADMINISTRADOR);
        UUID cuentaId = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, r.usuario().sub()).orElseThrow().getId();
        mvc.perform(delete("/api/admin/roles/cuenta/{cuentaId}", cuentaId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("motivo", "Conducta antideportiva"))))
                .andExpect(status().isNoContent());

        // Bloquear no borra la competición: sigue en el plantel donde estaba.
        mvc.perform(get("/api/supercopa/admin/equipo-torneo/{id}/miembros", a.inscripcion().getId())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].nombre", r.documento()).value(contains(NOMBRE_REGISTRADO)))
                .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].estado", r.documento()).value(contains("ACTIVO")));

        // Pero ningún delegado lo puede sumar a otro plantel.
        var b = mundo();
        agregar(b, cuerpo(r.documento(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JUGADOR_BLOQUEADO"));
        assertThat(jugadorEquipoRepository.findByPersonaIdAndTorneoId(persona(r.documento()).getId(), b.torneo().getId()))
                .isEmpty();
    }

    @Test
    void cuentaBloqueada_noSeApruebaSuSolicitud() throws Exception {
        var a = mundo();
        var r = registrado();
        UUID personaId = persona(r.documento()).getId();
        var solicitud = solicitudEquipoRepository.save(SolicitudEquipo.builder()
                .torneo(a.torneo()).equipoTorneo(a.inscripcion())
                .personaId(personaId)
                .build());
        var cuenta = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, r.usuario().sub()).orElseThrow();
        cuenta.setEstado(EstadoCuenta.BLOQUEADA);
        cuentaRepository.save(cuenta);

        mvc.perform(post("/api/supercopa/solicitudes/{id}/aprobar", solicitud.getId())
                        .header("Authorization", a.delegado().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JUGADOR_BLOQUEADO"));

        assertThat(jugadorEquipoRepository.findByPersonaIdAndTorneoId(personaId, a.torneo().getId())).isEmpty();
        assertThat(solicitudEquipoRepository.findById(solicitud.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    // ── Criterios 9 a 11 de la 0018 y 5 de la 0022 ──────────────────────

    @Test
    void registrarseDespues_asociaLaMismaPersona_veSuGol_yTodosMuestranSuNombre() throws Exception {
        var m = mundo();
        String doc = documentoAlAzar();
        agregar(m, cuerpo(doc, "Ana Pérez")).andExpect(status().isOk());
        UUID personaId = persona(doc).getId();

        // Juega un partido y hace un gol, todavía sin cuenta.
        var rival = otroEquipoEnElMismoTorneo(m);
        var partido = partidoRepository.save(Partido.builder()
                .torneo(m.torneo())
                .equipoLocalTorneo(m.inscripcion())
                .equipoVisitanteTorneo(rival.inscripcion())
                .fecha(LocalDateTime.now().minusDays(1))
                .estado(EstadoPartido.FINALIZADO)
                .build());
        partidoJugadorRepository.save(PartidoJugador.builder()
                .partido(partido).personaId(personaId)
                .equipoTorneo(m.inscripcion()).goles(1).jugo(true)
                .build());
        eventoPartidoRepository.save(EventoPartido.builder()
                .partido(partido).personaId(personaId).equipoTorneo(m.inscripcion())
                .tipoEvento(TipoEvento.GOL).orden(1)
                .build());

        // Se registra después, con el documento escrito con puntos: queda asociado a esa persona.
        var u = usuario();
        registrar(u, "CC", conPuntos(doc))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personaId").value(personaId.toString()));
        var cuenta = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow();
        assertThat(cuenta.getPersona().getId()).isEqualTo(personaId);
        assertThat(cuenta.getPersona().nombreCompleto()).isEqualTo(NOMBRE_REGISTRADO);

        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.goles").value(1))
                .andExpect(jsonPath("$.equipos[*].nombre").value(hasItem(m.inscripcion().getEquipo().getNombre())));

        mvc.perform(get("/api/supercopa/delegado/equipo-torneo/{id}/miembros", m.inscripcion().getId())
                        .header("Authorization", m.delegado().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].tieneCuenta", doc).value(contains(true)))
                .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].nombre", doc).value(contains(NOMBRE_REGISTRADO)));

        var admin = cuentaConRoles(Rol.ADMINISTRADOR);
        mvc.perform(get("/api/supercopa/admin/partidos/{id}/eventos", partido.getId())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personaId").value(personaId.toString()))
                .andExpect(jsonPath("$[0].jugadorNombre").value(NOMBRE_REGISTRADO));
        mvc.perform(get("/api/supercopa/admin/partidos/{id}/alineacion", partido.getId())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].jugadorNombre", doc).value(contains(NOMBRE_REGISTRADO)));
    }

    @Test
    void registrarseConElDocumentoDeOtraCuenta_409DocumentoRegistrado() throws Exception {
        var r = registrado();

        registrar(usuario(), "CC", r.documento())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REGISTRADO"));
    }

    @Test
    void plantel_delegadoYAdmin_traenNombreYTieneCuenta() throws Exception {
        var m = mundo();
        var r = registrado();
        String sinCuenta = documentoAlAzar();
        agregar(m, cuerpo(r.documento(), null)).andExpect(status().isOk());
        agregar(m, cuerpo(sinCuenta, "Sara Gómez")).andExpect(status().isOk());

        var admin = cuentaConRoles(Rol.ADMINISTRADOR);
        UUID id = m.inscripcion().getId();
        var pedidos = List.of(
                get("/api/supercopa/delegado/equipo-torneo/{id}/miembros", id)
                        .header("Authorization", m.delegado().bearer()),
                get("/api/supercopa/admin/equipo-torneo/{id}/miembros", id)
                        .header("Authorization", admin.bearer()));
        for (var pedido : pedidos) {
            mvc.perform(pedido)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].nombre", everyItem(notNullValue())))
                    .andExpect(jsonPath("$[*].personaId", everyItem(notNullValue())))
                    .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].tieneCuenta", r.documento()).value(contains(true)))
                    .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].tieneCuenta", sinCuenta).value(contains(false)))
                    .andExpect(jsonPath("$[?(@.numeroDocumento == '%s')].nombre", sinCuenta).value(contains("Sara Gómez")));
        }
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    /** Un torneo PUBLICADO con un equipo inscrito (PENDIENTE_PAGO) por su delegado registrado. */
    private record Mundo(Usuario delegado, Torneo torneo, EquipoTorneo inscripcion) {
    }

    /** Una persona registrada por el API: queda como "Ana María Pérez", con JUGADOR. */
    private record Registrado(Usuario usuario, String documento) {
    }

    private Mundo mundo() {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
        return inscribir(torneo);
    }

    private Mundo otroEquipoEnElMismoTorneo(Mundo m) {
        return inscribir(m.torneo());
    }

    private Mundo inscribir(Torneo torneo) {
        Usuario delegado = cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR);
        UUID delegadoId = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, delegado.sub())
                .orElseThrow().getPersona().getId();
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

    private Registrado registrado() throws Exception {
        var u = usuario();
        return new Registrado(u, registrar(u));
    }

    private Persona persona(String documento) {
        return personaRepository.findFirstByNumeroDocumento(documento).orElseThrow();
    }

    private ResultActions agregar(Mundo m, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros", m.inscripcion().getId())
                .header("Authorization", m.delegado().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    private static Map<String, Object> cuerpo(String documento, String nombre) {
        var c = new HashMap<String, Object>();
        c.put("numeroDocumento", documento);
        if (nombre != null) c.put("nombre", nombre);
        return c;
    }

    /** "1234567890" → "1.234.567.890". */
    private static String conPuntos(String doc) {
        return doc.charAt(0) + "." + doc.substring(1, 4) + "." + doc.substring(4, 7) + "." + doc.substring(7);
    }

    /** "1234567890" → " 1.234 567.890 ": el mismo documento, como lo escribiría alguien a mano. */
    private static String conPuntosYEspacios(String doc) {
        return " " + doc.charAt(0) + "." + doc.substring(1, 4) + " " + doc.substring(4, 7) + "." + doc.substring(7) + " ";
    }
}

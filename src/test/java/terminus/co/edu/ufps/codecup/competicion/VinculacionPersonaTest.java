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
import terminus.co.edu.ufps.codecup.identidad.model.EstadoVinculo;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.VinculoPersonaRepository;

/**
 * Spec 0023, criterios 6 a 11 (D1): registrarse con el documento de un miembro sin cuenta ya no
 * da su historial; abre una solicitud que aprueba el delegado de un equipo donde estuvo, o un admin.
 */
class VinculacionPersonaTest extends AuthTestSupport {

    private static final String NOMBRE_REGISTRADO = "Ana María Pérez"; // el que usa AuthTestSupport.registrar

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired VinculoPersonaRepository vinculoRepository;

    @Test
    void registrarseConDocumentoDelPlantel_abreVinculo_yAlAprobarloVeSuHistorial() throws Exception {
        var a = mundo();
        var miembro = agregarMiembro(a, "Pedro Plantel");

        var u = usuario();
        String registro = registrar(u, "CC", miembro.documento())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDocumento").doesNotExist())
                .andExpect(jsonPath("$.vinculoPendiente.personaId").value(miembro.personaId().toString()))
                .andExpect(jsonPath("$.vinculoPendiente.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        UUID temporal = UUID.fromString(objectMapper.readTree(registro).get("personaId").asText());
        String vinculoId = objectMapper.readTree(registro).get("vinculoPendiente").get("id").asText();
        assertThat(temporal).isNotEqualTo(miembro.personaId());

        // Mientras tanto no ve el historial de esa persona.
        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipos").isEmpty());
        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vinculoPendiente.id").value(vinculoId));

        // Su delegado la ve, con lo necesario para decidir.
        mvc.perform(get("/api/supercopa/vinculos/pendientes").header("Authorization", a.delegado().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].personaNombre", vinculoId).value(hasItem("Pedro Plantel")))
                .andExpect(jsonPath("$[?(@.id == '%s')].personaNumeroDocumento", vinculoId).value(hasItem(miembro.documento())))
                .andExpect(jsonPath("$[?(@.id == '%s')].solicitanteNombre", vinculoId).value(hasItem(NOMBRE_REGISTRADO)))
                .andExpect(jsonPath("$[?(@.id == '%s')].solicitanteCorreo", vinculoId).value(hasItem(u.correo())));

        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", vinculoId).header("Authorization", a.delegado().bearer()))
                .andExpect(status().isNoContent());

        var cuenta = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow();
        assertThat(cuenta.getPersona().getId()).isEqualTo(miembro.personaId());
        assertThat(cuenta.getPersona().nombreCompleto()).isEqualTo(NOMBRE_REGISTRADO);
        assertThat(personaRepository.findById(temporal)).isEmpty();
        mvc.perform(get("/api/supercopa/mi-perfil").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipos[*].nombre").value(hasItem(a.inscripcion().getEquipo().getNombre())));
        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.vinculoPendiente").doesNotExist())
                .andExpect(jsonPath("$.numeroDocumento").value(miembro.documento()));

        // Criterio 12: ya tiene cuenta, así que nadie más se registra con ese documento.
        registrar(usuario(), "CC", miembro.documento())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REGISTRADO"));
    }

    @Test
    void otroDelegado_noLaVe_niLaPuedeResolver() throws Exception {
        var a = mundo();
        var b = mundo();
        var miembro = agregarMiembro(a, "Pedro Ajeno");
        String vinculoId = registrarYObtenerVinculo(usuario(), miembro.documento());

        mvc.perform(get("/api/supercopa/vinculos/pendientes").header("Authorization", b.delegado().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(vinculoId))));
        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", vinculoId).header("Authorization", b.delegado().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VINCULO_AJENO"));
        mvc.perform(post("/api/supercopa/vinculos/{id}/rechazar", vinculoId).header("Authorization", b.delegado().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("motivo", "x"))))
                .andExpect(status().isForbidden());

        // Un admin sí.
        var admin = cuentaConRoles(Rol.ADMINISTRADOR);
        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", vinculoId).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rechazar_dejaLaCuentaConSuPersona() throws Exception {
        var a = mundo();
        var miembro = agregarMiembro(a, "Pedro Rechazado");
        var u = usuario();
        String vinculoId = registrarYObtenerVinculo(u, miembro.documento());
        UUID suPersona = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona().getId();

        mvc.perform(post("/api/supercopa/vinculos/{id}/rechazar", vinculoId).header("Authorization", a.delegado().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("motivo", "No es él"))))
                .andExpect(status().isNoContent());

        var vinculo = vinculoRepository.findById(UUID.fromString(vinculoId)).orElseThrow();
        assertThat(vinculo.getEstado()).isEqualTo(EstadoVinculo.RECHAZADA);
        assertThat(vinculo.getMotivoRechazo()).isEqualTo("No es él");
        assertThat(cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona().getId())
                .isEqualTo(suPersona);
        mvc.perform(get("/api/auth/me").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.vinculoPendiente").doesNotExist());
    }

    @Test
    void siLaPersonaTemporalYaJugo_409PersonaConParticipaciones() throws Exception {
        var a = mundo();
        var miembro = agregarMiembro(a, "Pedro Ocupado");
        var u = usuario();
        String vinculoId = registrarYObtenerVinculo(u, miembro.documento());

        // Con su persona temporal pidió unirse a otro equipo: ya tiene historial propio.
        var otro = mundo();
        mvc.perform(post("/api/supercopa/solicitudes").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("equipoTorneoId", otro.inscripcion().getId()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", vinculoId).header("Authorization", a.delegado().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERSONA_CON_PARTICIPACIONES"));
        assertThat(vinculoRepository.findById(UUID.fromString(vinculoId)).orElseThrow().getEstado())
                .isEqualTo(EstadoVinculo.PENDIENTE);
    }

    @Test
    void alAprobarUno_lasDemasSolicitudesHaciaEsaPersonaSeRechazan() throws Exception {
        var a = mundo();
        var miembro = agregarMiembro(a, "Pedro Disputado");
        String primero = registrarYObtenerVinculo(usuario(), miembro.documento());
        String segundo = registrarYObtenerVinculo(usuario(), miembro.documento());

        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", primero).header("Authorization", a.delegado().bearer()))
                .andExpect(status().isNoContent());

        assertThat(vinculoRepository.findById(UUID.fromString(segundo)).orElseThrow().getEstado())
                .isEqualTo(EstadoVinculo.RECHAZADA);
        mvc.perform(post("/api/supercopa/vinculos/{id}/aprobar", segundo).header("Authorization", a.delegado().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VINCULO_RESUELTO"));
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private record Mundo(Usuario delegado, Torneo torneo, EquipoTorneo inscripcion) {
    }

    private record Miembro(UUID personaId, String documento) {
    }

    private Mundo mundo() {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
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

    private Miembro agregarMiembro(Mundo m, String nombre) throws Exception {
        String doc = documentoAlAzar();
        String respuesta = mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros", m.inscripcion().getId())
                        .header("Authorization", m.delegado().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("numeroDocumento", doc, "nombre", nombre))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Miembro(UUID.fromString(objectMapper.readTree(respuesta).get("personaId").asText()), doc);
    }

    private String registrarYObtenerVinculo(Usuario u, String documento) throws Exception {
        String registro = registrar(u, "CC", documento)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(registro).get("vinculoPendiente").get("id").asText();
    }
}

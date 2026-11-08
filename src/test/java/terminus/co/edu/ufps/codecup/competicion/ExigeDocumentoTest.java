package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
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
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.SolicitudEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.SolicitudEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0023, criterios 2, 4 y 5: el documento es opcional, salvo en los torneos que el admin
 * marca con exigeDocumento (por defecto no lo exigen).
 */
class ExigeDocumentoTest extends AuthTestSupport {

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired SolicitudEquipoRepository solicitudEquipoRepository;

    @Test
    void torneoNuevo_noExigeDocumento_yElAdminLoCambia() throws Exception {
        var admin = cuentaConRoles(Rol.ADMINISTRADOR);
        String creado = mvc.perform(post("/api/supercopa/admin/torneos")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Copa " + UUID.randomUUID().toString().substring(0, 8), "edicion", 2026))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeDocumento").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(creado).get("id").asText();

        mvc.perform(put("/api/supercopa/admin/torneos/{id}/exige-documento", id)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("exigeDocumento", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeDocumento").value(true));
        assertThat(torneoRepository.findById(UUID.fromString(id)).orElseThrow().getExigeDocumento()).isTrue();
    }

    @Test
    void sinExigencia_miembrosSinDocumento_sonPersonasNuevas() throws Exception {
        var m = inscripcion(torneo(false), cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR));
        String nombre = "Sin Documento " + UUID.randomUUID().toString().substring(0, 6);

        String primero = agregar(m, Map.of("nombre", nombre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento").doesNotExist())
                .andExpect(jsonPath("$.tieneCuenta").value(false))
                .andReturn().getResponse().getContentAsString();
        // El mismo nombre en otro equipo es otra persona: sin documento no hay cómo saber que es la misma.
        var otro = inscripcion(m.torneo(), cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR));
        String segundo = agregar(otro, Map.of("nombre", nombre))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(primero).get("personaId").asText())
                .isNotEqualTo(objectMapper.readTree(segundo).get("personaId").asText());

        agregar(m, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOMBRE_REQUERIDO"));
    }

    @Test
    void conExigencia_agregarSinDocumento_400DocumentoRequerido() throws Exception {
        var m = inscripcion(torneo(true), cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR));

        agregar(m, Map.of("nombre", "Sin Documento"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REQUERIDO"));
        agregar(m, Map.of("nombre", "Con Documento", "numeroDocumento", documentoAlAzar()))
                .andExpect(status().isOk());
    }

    @Test
    void conExigencia_jugadorSinDocumentoNoPideUnirse() throws Exception {
        var m = inscripcion(torneo(true), cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR));
        var jugador = cuentaSinDocumento(Rol.JUGADOR);

        mvc.perform(post("/api/supercopa/solicitudes")
                        .header("Authorization", jugador.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("equipoTorneoId", m.inscripcion().getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REQUERIDO"));
    }

    @Test
    void conExigencia_delegadoSinDocumentoNoInscribe() throws Exception {
        var torneo = torneo(true);
        var delegado = cuentaSinDocumento(Rol.DELEGADO, Rol.JUGADOR);
        equipoRepository.save(Equipo.builder().nombre("Equipo sin doc " + UUID.randomUUID().toString().substring(0, 6))
                .delegadoPersonaId(personaDe(delegado)).build());

        mvc.perform(post("/api/supercopa/delegado/torneos/{id}/inscribir", torneo.getId())
                        .header("Authorization", delegado.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REQUERIDO"));
        assertThat(equipoTorneoRepository.findByTorneoIdAndDelegadoPersonaId(torneo.getId(), personaDe(delegado))).isEmpty();
    }

    @Test
    void conExigencia_noSeApruebaLaSolicitudDeAlguienSinDocumento() throws Exception {
        var torneo = torneo(false);
        var m = inscripcion(torneo, cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR));
        var jugador = cuentaSinDocumento(Rol.JUGADOR);
        var solicitud = solicitudEquipoRepository.save(SolicitudEquipo.builder()
                .torneo(torneo).equipoTorneo(m.inscripcion()).personaId(personaDe(jugador)).build());
        // El admin activa la exigencia después de la solicitud.
        torneo.setExigeDocumento(true);
        torneoRepository.save(torneo);

        mvc.perform(post("/api/supercopa/solicitudes/{id}/aprobar", solicitud.getId())
                        .header("Authorization", m.delegado().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTO_REQUERIDO"));
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private record Inscrito(Usuario delegado, Torneo torneo, EquipoTorneo inscripcion) {
    }

    private Torneo torneo(boolean exigeDocumento) {
        return torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .exigeDocumento(exigeDocumento)
                .build());
    }

    private Inscrito inscripcion(Torneo torneo, Usuario delegado) {
        UUID delegadoId = personaDe(delegado);
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + delegadoId.toString().substring(0, 8))
                .delegadoPersonaId(delegadoId)
                .build());
        var et = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(delegadoId)
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build());
        return new Inscrito(delegado, torneo, et);
    }

    private org.springframework.test.web.servlet.ResultActions agregar(Inscrito m, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/supercopa/delegado/equipo-torneo/{id}/miembros", m.inscripcion().getId())
                .header("Authorization", m.delegado().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    /** Como {@code cuentaConRoles}, pero con una persona registrada sin documento. */
    private Usuario cuentaSinDocumento(Rol... roles) {
        Usuario u = usuario();
        var persona = personaRepository.save(Persona.builder().nombres("Sin").apellidos("Documento").build());
        var cuenta = cuentaRepository.save(Cuenta.builder()
                .proveedor(Cuenta.PROVEEDOR_SUPABASE).proveedorSub(u.sub()).correo(u.correo())
                .persona(persona).estado(EstadoCuenta.ACTIVA).fechaCreacion(LocalDateTime.now())
                .build());
        for (Rol rol : roles) {
            cuentaRolRepository.save(CuentaRol.builder()
                    .cuenta(cuenta).rol(rol).estado(EstadoRol.APROBADO)
                    .fechaSolicitud(LocalDateTime.now()).fechaResolucion(LocalDateTime.now())
                    .build());
        }
        return u;
    }

    private UUID personaDe(Usuario u) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getPersona().getId();
    }
}

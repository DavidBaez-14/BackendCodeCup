package terminus.co.edu.ufps.codecup.finanzas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.finanzas.service.StorageService;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Spec 0001: un delegado solo pide URLs de Storage (subida y descarga) de lo suyo.
 * El admin descarga cualquier comprobante. Storage es un mock: aquí solo importa
 * quién llega a pedir la URL firmada.
 *
 * <p>El H2 se comparte entre clases: cada test arma su propio torneo con documentos al azar.
 */
class ComprobanteAccesoSecurityTest extends AuthTestSupport {

    private static final String BASE = "/api/finanzas/comprobantes";

    @Autowired TorneoRepository torneoRepository;
    @Autowired EquipoRepository equipoRepository;
    @Autowired EquipoTorneoRepository equipoTorneoRepository;
    @Autowired ComprobanteRepository comprobanteRepository;

    @MockBean StorageService storageService;

    @BeforeEach
    void storageResponde() {
        when(storageService.generarDownloadUrl(anyString())).thenReturn("https://storage/firmada");
        when(storageService.generarUploadUrl(any(UUID.class))).thenAnswer(inv ->
                new StorageService.UploadUrlResult("https://storage/subida", inv.getArgument(0) + "/comprobante"));
    }

    // ── download-url (criterios 1 a 3 y 6) ──────────────────────────────

    @Test
    void downloadUrl_comprobanteDeOtroDelegado_devuelve403() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var b = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var comprobante = comprobanteDe(a);

        descargar(comprobante.getId(), b.delegado())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPROBANTE_AJENO"))
                .andExpect(jsonPath("$.url").doesNotExist());
        verify(storageService, never()).generarDownloadUrl(anyString());
    }

    @Test
    void downloadUrl_comprobantePropio_devuelve200() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var comprobante = comprobanteDe(a);

        descargar(comprobante.getId(), a.delegado())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage/firmada"));
    }

    @Test
    void downloadUrl_comoAdminDelTorneo_devuelve200() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var comprobante = comprobanteDe(a);
        var admin = cuentaConRoles(Rol.JUGADOR);
        hacerAdmin(admin, a.inscripcion().getTorneo().getId());

        descargar(comprobante.getId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage/firmada"));
    }

    /** Spec 0024: el rol global ADMINISTRADOR ya no abre los comprobantes de un torneo ajeno. */
    @Test
    void downloadUrl_adminGlobalSinElTorneo_devuelve403() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var comprobante = comprobanteDe(a);

        descargar(comprobante.getId(), cuentaConRoles(Rol.ADMINISTRADOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPROBANTE_AJENO"));
    }

    @Test
    void downloadUrl_comprobanteInexistente_devuelve404() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        descargar(UUID.randomUUID(), a.delegado()).andExpect(status().isNotFound());
    }

    // ── upload-url (criterios 4 y 5) ────────────────────────────────────

    @Test
    void uploadUrl_equipoTorneoAjeno_devuelve403() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var b = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        pedirSubida(a.inscripcion().getId(), b.delegado())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSCRIPCION_AJENA"));
        verify(storageService, never()).generarUploadUrl(any(UUID.class));
    }

    @Test
    void uploadUrl_inscripcionPropiaPendienteDePago_devuelve200() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        pedirSubida(a.inscripcion().getId(), a.delegado())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://storage/subida"))
                .andExpect(jsonPath("$.fileKey").value(a.inscripcion().getId() + "/comprobante"));
    }

    @Test
    void uploadUrl_inscripcionYaAprobada_devuelve409_yNoSobrescribeElArchivo() throws Exception {
        var a = inscripcion(EstadoInscripcion.APROBADO);

        pedirSubida(a.inscripcion().getId(), a.delegado())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSCRIPCION_SIN_PAGO_PENDIENTE"));
        verify(storageService, never()).generarUploadUrl(any(UUID.class));
    }

    @Test
    void uploadUrl_inscripcionInexistente_devuelve404() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        pedirSubida(UUID.randomUUID(), a.delegado()).andExpect(status().isNotFound());
    }

    // ── subir (criterios 7 y 8) ─────────────────────────────────────────

    @Test
    void subir_conArchivoDeOtroEquipo_devuelve403_yNoGuarda() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var b = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        // b registra su inscripción apuntando al archivo de a, para luego descargarlo.
        subir(b.inscripcion().getId(), a.inscripcion().getId() + "/comprobante", b.delegado())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ARCHIVO_AJENO"));
        org.assertj.core.api.Assertions.assertThat(
                comprobanteRepository.findByReferenciaId(b.inscripcion().getId())).isEmpty();
    }

    @Test
    void subir_inscripcionAjena_devuelve403() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        var b = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        subir(a.inscripcion().getId(), a.inscripcion().getId() + "/comprobante", b.delegado())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSCRIPCION_AJENA"));
    }

    @Test
    void subir_conSuPropioArchivo_devuelve200() throws Exception {
        var a = inscripcion(EstadoInscripcion.PENDIENTE_PAGO);

        subir(a.inscripcion().getId(), a.inscripcion().getId() + "/comprobante", a.delegado())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private record Inscrito(Usuario delegado, UUID personaId, UUID cuentaId, EquipoTorneo inscripcion) {
    }

    /** Un delegado con su equipo inscrito en un torneo propio, en el estado dado. */
    private Inscrito inscripcion(EstadoInscripcion estado) {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
        Usuario delegado = cuentaConRoles(Rol.DELEGADO, Rol.JUGADOR);
        var cuenta = cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, delegado.sub()).orElseThrow();
        UUID personaId = cuenta.getPersona().getId();
        var equipo = equipoRepository.save(Equipo.builder()
                .nombre("Equipo " + personaId.toString().substring(0, 8))
                .delegadoPersonaId(personaId)
                .build());
        var et = equipoTorneoRepository.save(EquipoTorneo.builder()
                .torneo(torneo).equipo(equipo).delegadoPersonaId(personaId)
                .estadoInscripcion(estado)
                .build());
        return new Inscrito(delegado, personaId, cuenta.getId(), et);
    }

    private Comprobante comprobanteDe(Inscrito i) {
        return comprobanteRepository.save(Comprobante.builder()
                .tipo(TipoComprobante.INSCRIPCION)
                .referenciaId(i.inscripcion().getId())
                .urlArchivo(i.inscripcion().getId() + "/comprobante")
                .subidoPorCuentaId(i.cuentaId())
                .estado(EstadoComprobante.PENDIENTE_REVISION)
                .fechaSubida(LocalDateTime.now())
                .build());
    }

    private ResultActions descargar(UUID comprobanteId, Usuario u) throws Exception {
        return mvc.perform(get(BASE + "/{id}/download-url", comprobanteId)
                .header("Authorization", u.bearer()));
    }

    private ResultActions pedirSubida(UUID equipoTorneoId, Usuario u) throws Exception {
        return mvc.perform(post(BASE + "/upload-url")
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("equipoTorneoId", equipoTorneoId))));
    }

    private ResultActions subir(UUID referenciaId, String urlArchivo, Usuario u) throws Exception {
        return mvc.perform(post(BASE)
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("tipo", "INSCRIPCION", "referenciaId", referenciaId, "urlArchivo", urlArchivo))));
    }
}

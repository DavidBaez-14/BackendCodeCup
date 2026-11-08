package terminus.co.edu.ufps.codecup.finanzas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.dto.SubirComprobanteRequest;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.codecup.finanzas.model.Multa;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Spec 0014: upsert al reemplazar comprobante (sin duplicar filas), solo mientras la inscripcion
 * siga PENDIENTE_PAGO.
 * Spec 0003 (M3): aprobar sin efecto silencioso y MULTA cierra el circulo.
 */
@ExtendWith(MockitoExtension.class)
class ComprobanteServiceTest {

    @Mock ComprobanteRepository comprobanteRepo;
    @Mock EquipoTorneoRepository equipoTorneoRepo;
    @Mock MultaRepository multaRepo;
    @Mock DirectorioPersonas directorioPersonas;
    @InjectMocks ComprobanteService service;

    private static final UUID REF = UUID.randomUUID();
    // Spec 0022: el delegado se identifica por su persona; quien sube y quien resuelve, por su cuenta.
    private static final UUID DELEGADO_1 = UUID.randomUUID();
    private static final UUID DELEGADO_A = UUID.randomUUID();
    private static final UUID DELEGADO_B = UUID.randomUUID();
    private static final UUID CUENTA_1 = UUID.randomUUID();
    private static final UUID ADMIN_1 = UUID.randomUUID();

    private SubirComprobanteRequest req(String url) {
        var r = new SubirComprobanteRequest();
        r.setTipo("INSCRIPCION");
        r.setReferenciaId(REF);
        r.setUrlArchivo(url);
        return r;
    }

    private EquipoTorneo inscripcion(UUID delegado, EstadoInscripcion estado) {
        var et = new EquipoTorneo();
        et.setId(REF);
        et.setDelegadoPersonaId(delegado);
        et.setEstadoInscripcion(estado);
        return et;
    }

    @Test
    void subir_segundaVez_reemplazaMismoComprobante_noCreaOtro() {
        when(equipoTorneoRepo.findById(REF)).thenReturn(
                Optional.of(inscripcion(DELEGADO_1, EstadoInscripcion.PENDIENTE_PAGO)));
        var existente = Comprobante.builder()
                .id(UUID.randomUUID())
                .tipo(TipoComprobante.INSCRIPCION)
                .referenciaId(REF)
                .urlArchivo("vieja.jpg")
                .subidoPorCuentaId(CUENTA_1)
                .estado(EstadoComprobante.RECHAZADO)
                .motivoRechazo("borrosa")
                .build();
        when(comprobanteRepo.findByReferenciaId(REF)).thenReturn(List.of(existente));

        service.subir(req(REF + "/comprobante"), DELEGADO_1, CUENTA_1);

        var captor = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepo).save(captor.capture());
        var c = captor.getValue();
        assertThat(c.getId()).isEqualTo(existente.getId());   // mismo registro, no uno nuevo
        assertThat(c.getUrlArchivo()).isEqualTo(REF + "/comprobante");
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE_REVISION);
        assertThat(c.getMotivoRechazo()).isNull();
    }

    @Test
    void subir_primeraVez_creaComprobanteEnRevisionConFecha() {
        when(equipoTorneoRepo.findById(REF)).thenReturn(
                Optional.of(inscripcion(DELEGADO_1, EstadoInscripcion.PENDIENTE_PAGO)));
        when(comprobanteRepo.findByReferenciaId(REF)).thenReturn(List.of());

        service.subir(req(REF + "/comprobante"), DELEGADO_1, CUENTA_1);

        var captor = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepo).save(captor.capture());
        var c = captor.getValue();
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE_REVISION);
        assertThat(c.getFechaSubida()).isNotNull();  // se setea explicito (new Comprobante no aplica @Builder.Default)
    }

    @Test
    void subir_inscripcionNoPendiente_lanzaError_yNoGuarda() {
        when(equipoTorneoRepo.findById(REF)).thenReturn(
                Optional.of(inscripcion(DELEGADO_1, EstadoInscripcion.APROBADO)));

        assertThatThrownBy(() -> service.subir(req(REF + "/comprobante"), DELEGADO_1, CUENTA_1))
                .isInstanceOf(IllegalStateException.class);
        verify(comprobanteRepo, never()).save(any());
    }

    // ── M3 (spec 0003): aprobar sin efecto silencioso + MULTA cierra el circulo ──

    private Comprobante comprobante(UUID id, TipoComprobante tipo, UUID ref) {
        return Comprobante.builder()
                .id(id).tipo(tipo).referenciaId(ref)
                .subidoPorCuentaId(CUENTA_1)
                .estado(EstadoComprobante.PENDIENTE_REVISION)
                .build();
    }

    @Test
    void aprobar_inscripcionPendiente_apruebaInscripcionYComprobante() {
        var compId = UUID.randomUUID();
        var comp = comprobante(compId, TipoComprobante.INSCRIPCION, REF);
        when(comprobanteRepo.findById(compId)).thenReturn(Optional.of(comp));
        var et = inscripcion(DELEGADO_1, EstadoInscripcion.PENDIENTE_PAGO);
        when(equipoTorneoRepo.findById(REF)).thenReturn(Optional.of(et));

        service.aprobar(compId, ADMIN_1);

        assertThat(et.getEstadoInscripcion()).isEqualTo(EstadoInscripcion.APROBADO);
        assertThat(comp.getEstado()).isEqualTo(EstadoComprobante.APROBADO);
    }

    @Test
    void aprobar_inscripcionYaNoPendiente_lanzaError_noApruebaEnSilencio() {
        var compId = UUID.randomUUID();
        var comp = comprobante(compId, TipoComprobante.INSCRIPCION, REF);
        when(comprobanteRepo.findById(compId)).thenReturn(Optional.of(comp));
        when(equipoTorneoRepo.findById(REF)).thenReturn(
                Optional.of(inscripcion(DELEGADO_1, EstadoInscripcion.APROBADO)));

        assertThatThrownBy(() -> service.aprobar(compId, ADMIN_1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(comp.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE_REVISION); // sin cambios
    }

    @Test
    void aprobar_comprobanteMulta_marcaMultaPagada() {
        var compId = UUID.randomUUID();
        var multaId = UUID.randomUUID();
        var comp = comprobante(compId, TipoComprobante.MULTA, multaId);
        when(comprobanteRepo.findById(compId)).thenReturn(Optional.of(comp));
        var multa = new Multa();
        multa.setId(multaId);
        multa.setEstado(EstadoMulta.PENDIENTE);
        when(multaRepo.findById(multaId)).thenReturn(Optional.of(multa));

        service.aprobar(compId, ADMIN_1);

        assertThat(multa.getEstado()).isEqualTo(EstadoMulta.PAGADA);
        assertThat(multa.getFechaPago()).isNotNull();
        verify(multaRepo).save(multa);
    }

    @Test
    void subir_multaDeOtroDelegado_lanzaError() {
        var multaId = UUID.randomUUID();
        var etId = UUID.randomUUID();
        var r = new SubirComprobanteRequest();
        r.setTipo("MULTA");
        r.setReferenciaId(multaId);
        r.setUrlArchivo("foto.jpg");
        var multa = new Multa();
        multa.setId(multaId);
        multa.setEquipoTorneoId(etId);
        multa.setEstado(EstadoMulta.PENDIENTE);
        when(multaRepo.findById(multaId)).thenReturn(Optional.of(multa));
        when(equipoTorneoRepo.findById(etId)).thenReturn(
                Optional.of(inscripcion(DELEGADO_A, EstadoInscripcion.PENDIENTE_PAGO)));

        assertThatThrownBy(() -> service.subir(r, DELEGADO_B, CUENTA_1))
                .isInstanceOf(RuntimeException.class);
        verify(comprobanteRepo, never()).save(any());
    }

    // ── Spec 0001: el archivo debe estar en la carpeta del propio equipo ──

    @Test
    void subir_multaPropiaConArchivoDeOtraCarpeta_lanza403ArchivoAjeno_yNoGuarda() {
        var multaId = UUID.randomUUID();
        var etId = UUID.randomUUID();
        var r = new SubirComprobanteRequest();
        r.setTipo("MULTA");
        r.setReferenciaId(multaId);
        r.setUrlArchivo(UUID.randomUUID() + "/comprobante"); // carpeta de otro equipo
        var multa = new Multa();
        multa.setId(multaId);
        multa.setEquipoTorneoId(etId);
        multa.setEstado(EstadoMulta.PENDIENTE);
        when(multaRepo.findById(multaId)).thenReturn(Optional.of(multa));
        var et = inscripcion(DELEGADO_A, EstadoInscripcion.APROBADO);
        et.setId(etId);
        when(equipoTorneoRepo.findById(etId)).thenReturn(Optional.of(et));

        assertThatThrownBy(() -> service.subir(r, DELEGADO_A, CUENTA_1))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ARCHIVO_AJENO");
        verify(comprobanteRepo, never()).save(any());
    }

    @Test
    void subir_conSaltoDeRuta_lanza403ArchivoAjeno() {
        when(equipoTorneoRepo.findById(REF)).thenReturn(
                Optional.of(inscripcion(DELEGADO_1, EstadoInscripcion.PENDIENTE_PAGO)));

        assertThatThrownBy(() -> service.subir(req(REF + "/../otro/comprobante"), DELEGADO_1, CUENTA_1))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ARCHIVO_AJENO");
        verify(comprobanteRepo, never()).save(any());
    }
}

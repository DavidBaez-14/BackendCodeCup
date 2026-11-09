package terminus.co.edu.ufps.codecup.competicion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TituloRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;

/**
 * Spec 0014: al aprobar/rechazar la inscripcion desde el panel de torneo, el comprobante
 * PENDIENTE_REVISION asociado debe resolverse (no quedar colgado "En revision").
 */
@ExtendWith(MockitoExtension.class)
class TorneoAdminServiceComprobanteSyncTest {

    @Mock EquipoTorneoRepository equipoTorneoRepo;
    @Mock ComprobanteRepository comprobanteRepo;
    // Repos restantes del servicio; no intervienen en aprobar/rechazar pero @InjectMocks los exige.
    @Mock TorneoRepository torneoRepo;
    @Mock PartidoRepository partidoRepo;
    @Mock PartidoJugadorRepository partidoJugadorRepo;
    @Mock EventoPartidoRepository eventoPartidoRepo;
    @Mock DirectorioPersonas directorioPersonas;
    @Mock TituloRepository tituloRepo;
    @Mock ReglasInscripcion reglasInscripcion;
    @InjectMocks TorneoAdminService service;

    private static final UUID TORNEO = UUID.randomUUID();
    private static final UUID ET = UUID.randomUUID();

    private EquipoTorneo inscripcionPendiente() {
        var t = new Torneo();
        t.setId(TORNEO);
        var et = new EquipoTorneo();
        et.setId(ET);
        et.setTorneo(t);
        et.setEquipo(new Equipo());           // toInscripcionDTO lee et.getEquipo()
        et.setEstadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO);
        return et;
    }

    private Comprobante comprobantePendiente() {
        return Comprobante.builder()
                .id(UUID.randomUUID())
                .referenciaId(ET)
                .estado(EstadoComprobante.PENDIENTE_REVISION)
                .build();
    }

    @Test
    void aprobarInscripcion_resuelveComprobantePendiente_aAprobado() {
        when(equipoTorneoRepo.findById(ET)).thenReturn(Optional.of(inscripcionPendiente()));
        var comp = comprobantePendiente();
        when(comprobanteRepo.findByReferenciaId(ET)).thenReturn(List.of(comp));

        service.aprobarInscripcion(TORNEO, ET, UUID.randomUUID());

        assertThat(comp.getEstado()).isEqualTo(EstadoComprobante.APROBADO);
        verify(comprobanteRepo).save(comp);
    }

    @Test
    void rechazarInscripcion_resuelveComprobantePendiente_aRechazadoConMotivo() {
        when(equipoTorneoRepo.findById(ET)).thenReturn(Optional.of(inscripcionPendiente()));
        var comp = comprobantePendiente();
        when(comprobanteRepo.findByReferenciaId(ET)).thenReturn(List.of(comp));

        service.rechazarInscripcion(TORNEO, ET, "documento ilegible");

        assertThat(comp.getEstado()).isEqualTo(EstadoComprobante.RECHAZADO);
        assertThat(comp.getMotivoRechazo()).isEqualTo("documento ilegible");
        verify(comprobanteRepo).save(comp);
    }
}

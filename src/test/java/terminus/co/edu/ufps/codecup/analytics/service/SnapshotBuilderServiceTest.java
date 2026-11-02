package terminus.co.edu.ufps.codecup.analytics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import terminus.co.edu.ufps.codecup.competicion.dto.admin.PosicionGrupoDTO;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPosicion;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotGoleadorRepository;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPorteroRepository;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPosicionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Criterio 4 de la spec 0015.
 *
 * <p>Antes de la fusión, la reconstrucción de snapshots <b>nunca persistía nada</b>:
 * el cliente HTTP pedía {@code /api/supercopa/internal/torneos/{id}/clasificacion},
 * un endpoint que MS2 jamás implementó, y el 404 se tragaba en un {@code catch} que
 * devolvía mapa vacío. Este test fija el comportamiento nuevo: lo que devuelve
 * {@code ClasificacionService} termina, campo por campo, en {@code analytics.snapshot_posicion}.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotBuilderServiceTest {

    @Mock private CompeticionDatosService datos;
    @Mock private SnapshotPosicionRepository posicionRepo;
    @Mock private SnapshotGoleadorRepository goleadorRepo;
    @Mock private SnapshotPorteroRepository porteroRepo;

    @InjectMocks private SnapshotBuilderService service;

    private final UUID torneoId = UUID.randomUUID();

    @Test
    void persisteUnaPosicionPorEquipoYGrupo() {
        UUID equipoA1 = UUID.randomUUID();
        UUID equipoB1 = UUID.randomUUID();

        when(datos.obtenerClasificacion(torneoId)).thenReturn(Map.of(
                "A", List.of(posicion(1, equipoA1, "Sistemas FC", 9, 3, 3, 0, 0, 12, 2, 10, 1)),
                "B", List.of(posicion(1, equipoB1, "Civil FC", 6, 3, 2, 0, 1, 7, 5, 2, 0))));

        service.reconstruir(torneoId);

        verify(posicionRepo).deleteByTorneoId(torneoId);

        ArgumentCaptor<SnapshotPosicion> captor = ArgumentCaptor.forClass(SnapshotPosicion.class);
        verify(posicionRepo, times(2)).save(captor.capture());

        SnapshotPosicion sistemas = captor.getAllValues().stream()
                .filter(s -> equipoA1.equals(s.getEquipoTorneoId()))
                .findFirst()
                .orElseThrow();

        assertThat(sistemas.getTorneoId()).isEqualTo(torneoId);
        assertThat(sistemas.getGrupo()).isEqualTo("A");
        assertThat(sistemas.getEquipoNombre()).isEqualTo("Sistemas FC");
        assertThat(sistemas.getPosicion()).isEqualTo(1);
        assertThat(sistemas.getPts()).isEqualTo(9);
        assertThat(sistemas.getPj()).isEqualTo(3);
        assertThat(sistemas.getPg()).isEqualTo(3);
        assertThat(sistemas.getPe()).isZero();
        assertThat(sistemas.getPp()).isZero();
        assertThat(sistemas.getGf()).isEqualTo(12);
        assertThat(sistemas.getGc()).isEqualTo(2);
        assertThat(sistemas.getDg()).isEqualTo(10);
        assertThat(sistemas.getRojas()).isEqualTo(1);
        assertThat(sistemas.getForm()).isEqualTo("W,W,W");
        assertThat(sistemas.getDescalificado()).isFalse();
    }

    @Test
    void clasificacionVaciaNoBorraLosSnapshotsExistentes() {
        when(datos.obtenerClasificacion(torneoId)).thenReturn(Map.of());

        service.reconstruir(torneoId);

        // Si MS2 no pudo calcular la tabla, conservar la última buena es mejor
        // que dejar al visitante con una tabla en blanco.
        verify(posicionRepo, never()).deleteByTorneoId(torneoId);
        verify(posicionRepo, never()).save(any());
    }

    @Test
    void goleadoresYPorterosSiguenVaciosHastaLaSpec0016() {
        when(datos.obtenerClasificacion(torneoId)).thenReturn(Map.of());
        when(datos.obtenerGoleadores(torneoId)).thenReturn(List.of());
        when(datos.obtenerPorteros(torneoId)).thenReturn(List.of());

        service.reconstruir(torneoId);

        verify(goleadorRepo).deleteByTorneoId(torneoId);
        verify(goleadorRepo, never()).save(any());
        verify(porteroRepo).deleteByTorneoId(torneoId);
        verify(porteroRepo, never()).save(any());
    }

    private PosicionGrupoDTO posicion(int pos, UUID equipoTorneoId, String nombre,
                                      int pts, int pj, int pg, int pe, int pp,
                                      int gf, int gc, int dg, int rojas) {
        return PosicionGrupoDTO.builder()
                .posicion(pos)
                .equipoTorneoId(equipoTorneoId)
                .equipoNombre(nombre)
                .grupo("A")
                .pts(pts).pj(pj).pg(pg).pe(pe).pp(pp)
                .gf(gf).gc(gc).dg(dg).rojas(rojas)
                .form(List.of("W", "W", "W"))
                .descalificado(false)
                .build();
    }
}

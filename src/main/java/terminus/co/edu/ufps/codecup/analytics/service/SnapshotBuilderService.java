package terminus.co.edu.ufps.codecup.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.competicion.dto.admin.PosicionGrupoDTO;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotGoleador;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPortero;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPosicion;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotGoleadorRepository;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPorteroRepository;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPosicionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recalcula los snapshots de un torneo leyendo los datos de competición en proceso.
 * Es idempotente: borra los snapshots viejos del torneo y mete los nuevos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotBuilderService {

    private final CompeticionDatosService datos;
    private final SnapshotPosicionRepository posicionRepo;
    private final SnapshotGoleadorRepository goleadorRepo;
    private final SnapshotPorteroRepository porteroRepo;

    @Async
    @Transactional
    public void reconstruirAsync(UUID torneoId) {
        reconstruir(torneoId);
    }

    @Transactional
    public void reconstruir(UUID torneoId) {
        log.info("Reconstruyendo snapshots del torneo {}", torneoId);
        reconstruirPosiciones(torneoId);
        reconstruirGoleadores(torneoId);
        reconstruirPorteros(torneoId);
        log.info("Snapshots torneo {} OK", torneoId);
    }

    private void reconstruirPosiciones(UUID torneoId) {
        Map<String, List<PosicionGrupoDTO>> data = datos.obtenerClasificacion(torneoId);
        if (data == null || data.isEmpty()) {
            log.warn("Clasificación vacía o nula para torneo {}", torneoId);
            return;
        }
        posicionRepo.deleteByTorneoId(torneoId);
        for (var entry : data.entrySet()) {
            String grupo = entry.getKey();
            for (PosicionGrupoDTO row : entry.getValue()) {
                posicionRepo.save(SnapshotPosicion.builder()
                        .torneoId(torneoId)
                        .grupo(grupo)
                        .equipoTorneoId(row.getEquipoTorneoId())
                        .equipoNombre(row.getEquipoNombre())
                        .posicion(row.getPosicion())
                        .pts(row.getPts())
                        .pj(row.getPj())
                        .pg(row.getPg())
                        .pe(row.getPe())
                        .pp(row.getPp())
                        .gf(row.getGf())
                        .gc(row.getGc())
                        .dg(row.getDg())
                        .rojas(row.getRojas())
                        .form(serializeForm(row.getForm()))
                        .descalificado(Boolean.TRUE.equals(row.getDescalificado()))
                        .build());
            }
        }
    }

    private void reconstruirGoleadores(UUID torneoId) {
        var data = datos.obtenerGoleadores(torneoId);
        goleadorRepo.deleteByTorneoId(torneoId);
        int pos = 1;
        for (var row : data) {
            goleadorRepo.save(SnapshotGoleador.builder()
                    .torneoId(torneoId)
                    .personaId(row.personaId())
                    .jugadorNombre(row.jugadorNombre())
                    .equipoNombre(row.equipoNombre())
                    .goles(row.goles())
                    .posicion(pos++)
                    .build());
        }
    }

    private void reconstruirPorteros(UUID torneoId) {
        var data = datos.obtenerPorteros(torneoId);
        porteroRepo.deleteByTorneoId(torneoId);
        int pos = 1;
        for (var row : data) {
            int gc = row.golesEnContra();
            int pj = row.partidosJugados();
            BigDecimal promedio = pj == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf((double) gc / pj).setScale(2, RoundingMode.HALF_UP);
            porteroRepo.save(SnapshotPortero.builder()
                    .torneoId(torneoId)
                    .equipoTorneoId(row.equipoTorneoId())
                    .equipoNombre(row.equipoNombre())
                    .golesEnContra(gc)
                    .partidosJugados(pj)
                    .promedioGc(promedio)
                    .posicion(pos++)
                    .build());
        }
    }

    private String serializeForm(List<String> form) {
        if (form == null || form.isEmpty()) return null;
        return String.join(",", form);
    }
}

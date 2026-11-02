package terminus.co.edu.ufps.codecup.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import terminus.co.edu.ufps.codecup.competicion.dto.admin.AsignarPremioRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.PremioDTO;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotGoleador;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPortero;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotGoleadorRepository;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPorteroRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TorneoCerradoService {

    private final CompeticionDatosService datos;
    private final SnapshotGoleadorRepository goleadorRepo;
    private final SnapshotPorteroRepository porteroRepo;

    @Async
    public void procesarCierre(UUID torneoId) {
        log.info("Procesando cierre de torneo {} para premios automáticos", torneoId);

        List<PremioDTO> premios = datos.obtenerPremios(torneoId);
        if (premios.isEmpty()) {
            log.warn("No se encontraron premios para torneo {}; se omite asignación", torneoId);
            return;
        }

        for (PremioDTO premio : premios) {
            String codigo = premio.getCodigoCatalogo();
            UUID torneoPremioId = premio.getId();
            if (torneoPremioId == null || codigo == null) continue;

            try {
                switch (codigo) {
                    case "GOLEADOR" -> asignarGoleador(torneoId, torneoPremioId);
                    case "PORTERO" -> asignarPortero(torneoId, torneoPremioId);
                    case "CAMPEON", "SUBCAMPEON", "TERCERO" ->
                            asignarPodio(torneoId, torneoPremioId, codigo);
                    default -> log.debug("Premio {} se omite (asignación manual o no automática)", codigo);
                }
            } catch (Exception e) {
                log.error("Error asignando premio {} para torneo {}: {}", codigo, torneoId, e.getMessage());
            }
        }

        log.info("Cierre de torneo {} procesado", torneoId);
    }

    private void asignarGoleador(UUID torneoId, UUID torneoPremioId) {
        List<SnapshotGoleador> top = goleadorRepo.findByTorneoIdOrderByGolesDescJugadorNombreAsc(
                torneoId, PageRequest.of(0, 1));
        if (top.isEmpty()) {
            log.warn("No hay goleadores para torneo {}", torneoId);
            return;
        }
        SnapshotGoleador g = top.get(0);
        datos.asignarPremio(torneoId, torneoPremioId, porCedula(g.getCedula()));
        log.info("Goleador asignado: {} ({} goles)", g.getCedula(), g.getGoles());
    }

    private void asignarPortero(UUID torneoId, UUID torneoPremioId) {
        List<SnapshotPortero> top = porteroRepo.findByTorneoIdOrderByPromedioGcAscEquipoNombreAsc(
                torneoId, PageRequest.of(0, 1));
        if (top.isEmpty()) {
            log.warn("No hay porteros para torneo {}", torneoId);
            return;
        }
        SnapshotPortero p = top.get(0);
        datos.asignarPremio(torneoId, torneoPremioId, porEquipo(p.getEquipoTorneoId()));
        log.info("Portero asignado: equipo {} (promedio {})", p.getEquipoNombre(), p.getPromedioGc());
    }

    private void asignarPodio(UUID torneoId, UUID torneoPremioId, String codigo) {
        List<CompeticionDatosService.ResultadoPartido> partidos = datos.obtenerPartidos(torneoId);
        if (partidos.isEmpty()) {
            log.warn("No hay partidos para determinar podio del torneo {}", torneoId);
            return;
        }

        String faseFinal = switch (codigo) {
            case "CAMPEON", "SUBCAMPEON" -> "FINAL";
            case "TERCERO" -> "TERCER_PUESTO";
            default -> null;
        };
        if (faseFinal == null) return;

        boolean buscarPerdedor = "SUBCAMPEON".equals(codigo);

        for (var p : partidos) {
            if (!faseFinal.equals(p.fase())) continue;
            String estado = p.estado();
            if (!"FINALIZADO".equals(estado) && !"WO".equals(estado)) continue;

            if (p.golesLocal() == p.golesVisitante()) continue;

            // FIXME (spec 0016): el id que se asigna no corresponde necesariamente al
            // equipo ganador/perdedor calculado — asume que el local siempre gana.
            // Se porta tal cual desde MS4 para no cambiar comportamiento en esta fusión.
            // Es código inerte hoy: nadie invoca el webhook que llega hasta aquí.
            UUID equipoAsignar = buscarPerdedor
                    ? p.equipoVisitanteTorneoId()
                    : p.equipoLocalTorneoId();
            if (equipoAsignar == null) continue;

            datos.asignarPremio(torneoId, torneoPremioId, porEquipo(equipoAsignar));
            log.info("{} asignado: equipoTorneo {}", codigo, equipoAsignar);
            return;
        }

        log.warn("No se encontró partido de {} para torneo {}", faseFinal, torneoId);
    }

    private static AsignarPremioRequest porCedula(String cedula) {
        var req = new AsignarPremioRequest();
        req.setCedula(cedula);
        return req;
    }

    private static AsignarPremioRequest porEquipo(UUID equipoTorneoId) {
        var req = new AsignarPremioRequest();
        req.setEquipoTorneoId(equipoTorneoId);
        return req;
    }
}

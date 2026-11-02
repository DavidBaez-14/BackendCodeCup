package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.competicion.dto.admin.PosicionGrupoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FormatoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.SlotPartido;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuando termina la fase de grupos, este servicio puebla los placeholders del bracket
 * con los equipos clasificados, siguiendo el reglamento de
 * docs/reglas_rondas_eliminacion.md.
 *
 * Soporta:
 *  - CHAMPIONS con numGrupos=2, clasifican=6, repechaje=true (caso del demo).
 *  - GRUPOS_ELIMINATORIAS con numGrupos=2, clasifican ∈ {2, 4}.
 *
 * Es idempotente: si la primera ronda KO ya tiene equipos asignados, no hace nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BracketAutoFillService {

    private final TorneoRepository torneoRepo;
    private final PartidoRepository partidoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final ClasificacionService clasificacionService;

    @Transactional
    public void poblarSiFaseGruposCompleta(UUID torneoId) {
        Torneo torneo = torneoRepo.findById(torneoId).orElse(null);
        if (torneo == null) return;
        if (torneo.getFormato() != FormatoTorneo.CHAMPIONS
                && torneo.getFormato() != FormatoTorneo.GRUPOS_ELIMINATORIAS) {
            return;
        }
        if (torneo.getNumGrupos() == null || torneo.getNumGrupos() != 2) {
            log.info("BracketAutoFill: torneo {} tiene numGrupos != 2, no se procesa.", torneoId);
            return;
        }
        if (!clasificacionService.faseGruposCompleta(torneoId)) return;

        List<Partido> todos = partidoRepo.findByTorneoId(torneoId);
        if (yaPoblado(todos)) {
            log.info("BracketAutoFill: torneo {} ya tiene bracket poblado, skip.", torneoId);
            return;
        }

        Map<String, List<PosicionGrupoDTO>> clas = clasificacionService.calcularPorGrupo(torneoId);
        List<PosicionGrupoDTO> grA = clas.get("A");
        List<PosicionGrupoDTO> grB = clas.get("B");
        if (grA == null || grB == null) {
            log.warn("BracketAutoFill: no se encontraron grupos A/B para torneo {}", torneoId);
            return;
        }

        Map<UUID, EquipoTorneo> indexEt = indexarEquiposTorneo(torneoId);

        boolean conRepechaje = Boolean.TRUE.equals(torneo.getRepechaje())
                && torneo.getFormato() == FormatoTorneo.CHAMPIONS;

        if (conRepechaje) {
            poblarChampionsConRepechaje(todos, grA, grB, indexEt);
        } else {
            poblarGruposEliminatorias(todos, grA, grB, indexEt, torneo);
        }

        partidoRepo.saveAll(todos);
        log.info("BracketAutoFill: torneo {} bracket poblado correctamente.", torneoId);
    }

    private boolean yaPoblado(List<Partido> partidos) {
        // Si CUALQUIER partido KO (no GRUPOS) tiene equipo local asignado, asumimos que ya se pobló
        return partidos.stream()
                .anyMatch(p -> p.getFase() != null
                        && p.getFase() != FaseTorneo.GRUPOS
                        && p.getEquipoLocalTorneo() != null);
    }

    private Map<UUID, EquipoTorneo> indexarEquiposTorneo(UUID torneoId) {
        Map<UUID, EquipoTorneo> idx = new java.util.HashMap<>();
        for (EquipoTorneo et : equipoTorneoRepo.findByTorneoIdOrderByFechaInscripcionAsc(torneoId)) {
            idx.put(et.getId(), et);
        }
        return idx;
    }

    /**
     * CHAMPIONS con repechaje (6 clasifican por grupo).
     * R1: 3A vs 6B    R2: 4A vs 5B    R3: 3B vs 6A    R4: 4B vs 5A
     * C1: 1A vs Win(R4)  C2: 2B vs Win(R1)  C3: 1B vs Win(R2)  C4: 2A vs Win(R3)
     */
    private void poblarChampionsConRepechaje(
            List<Partido> todos,
            List<PosicionGrupoDTO> grA,
            List<PosicionGrupoDTO> grB,
            Map<UUID, EquipoTorneo> indexEt) {

        List<Partido> repechajes = ordenadosPorFecha(todos, FaseTorneo.REPECHAJE);
        List<Partido> cuartos = ordenadosPorFecha(todos, FaseTorneo.CUARTOS);
        if (repechajes.size() < 4 || cuartos.size() < 4) {
            log.warn("BracketAutoFill CHAMPIONS: faltan placeholders (rep={}, cf={}).",
                    repechajes.size(), cuartos.size());
            return;
        }
        if (grA.size() < 6 || grB.size() < 6) {
            log.warn("BracketAutoFill CHAMPIONS: clasificación incompleta (A={}, B={}).",
                    grA.size(), grB.size());
            return;
        }

        EquipoTorneo a1 = et(indexEt, grA.get(0));
        EquipoTorneo a2 = et(indexEt, grA.get(1));
        EquipoTorneo a3 = et(indexEt, grA.get(2));
        EquipoTorneo a4 = et(indexEt, grA.get(3));
        EquipoTorneo a5 = et(indexEt, grA.get(4));
        EquipoTorneo a6 = et(indexEt, grA.get(5));
        EquipoTorneo b1 = et(indexEt, grB.get(0));
        EquipoTorneo b2 = et(indexEt, grB.get(1));
        EquipoTorneo b3 = et(indexEt, grB.get(2));
        EquipoTorneo b4 = et(indexEt, grB.get(3));
        EquipoTorneo b5 = et(indexEt, grB.get(4));
        EquipoTorneo b6 = et(indexEt, grB.get(5));

        // Repechajes
        Partido r1 = repechajes.get(0);
        Partido r2 = repechajes.get(1);
        Partido r3 = repechajes.get(2);
        Partido r4 = repechajes.get(3);
        r1.setEquipoLocalTorneo(a3); r1.setEquipoVisitanteTorneo(b6);
        r2.setEquipoLocalTorneo(a4); r2.setEquipoVisitanteTorneo(b5);
        r3.setEquipoLocalTorneo(b3); r3.setEquipoVisitanteTorneo(a6);
        r4.setEquipoLocalTorneo(b4); r4.setEquipoVisitanteTorneo(a5);

        // Cuartos
        Partido c1 = cuartos.get(0);
        Partido c2 = cuartos.get(1);
        Partido c3 = cuartos.get(2);
        Partido c4 = cuartos.get(3);
        c1.setEquipoLocalTorneo(a1);
        c2.setEquipoLocalTorneo(b2);
        c3.setEquipoLocalTorneo(b1);
        c4.setEquipoLocalTorneo(a2);

        // Re-encadenar repechajes a cuartos según reglamento.
        // El defaul binario era R1→C1, R2→C2, R3→C3, R4→C4. Lo sobrescribimos:
        r1.setSiguientePartido(c2); r1.setSiguienteSlot(SlotPartido.VISITANTE);
        r2.setSiguientePartido(c3); r2.setSiguienteSlot(SlotPartido.VISITANTE);
        r3.setSiguientePartido(c4); r3.setSiguienteSlot(SlotPartido.VISITANTE);
        r4.setSiguientePartido(c1); r4.setSiguienteSlot(SlotPartido.VISITANTE);
    }

    /**
     * GRUPOS_ELIMINATORIAS sin repechaje: cruce clásico campeón vs subcampeón cruzado.
     * Si clasifican=2 → primera KO = SEMIS:
     *   S1: 1A vs 2B    S2: 1B vs 2A
     * Si clasifican=4 → primera KO = CUARTOS:
     *   C1: 1A vs 4B    C2: 2A vs 3B    C3: 1B vs 4A    C4: 2B vs 3A
     */
    private void poblarGruposEliminatorias(
            List<Partido> todos,
            List<PosicionGrupoDTO> grA,
            List<PosicionGrupoDTO> grB,
            Map<UUID, EquipoTorneo> indexEt,
            Torneo torneo) {

        int clasifican = torneo.getClasificanPorGrupo() == null ? 2 : torneo.getClasificanPorGrupo();
        if (clasifican == 2) {
            List<Partido> semis = ordenadosPorFecha(todos, FaseTorneo.SEMIS);
            if (semis.size() < 2 || grA.size() < 2 || grB.size() < 2) return;
            semis.get(0).setEquipoLocalTorneo(et(indexEt, grA.get(0)));
            semis.get(0).setEquipoVisitanteTorneo(et(indexEt, grB.get(1)));
            semis.get(1).setEquipoLocalTorneo(et(indexEt, grB.get(0)));
            semis.get(1).setEquipoVisitanteTorneo(et(indexEt, grA.get(1)));
        } else if (clasifican >= 4) {
            List<Partido> cuartos = ordenadosPorFecha(todos, FaseTorneo.CUARTOS);
            if (cuartos.size() < 4 || grA.size() < 4 || grB.size() < 4) return;
            cuartos.get(0).setEquipoLocalTorneo(et(indexEt, grA.get(0)));
            cuartos.get(0).setEquipoVisitanteTorneo(et(indexEt, grB.get(3)));
            cuartos.get(1).setEquipoLocalTorneo(et(indexEt, grA.get(1)));
            cuartos.get(1).setEquipoVisitanteTorneo(et(indexEt, grB.get(2)));
            cuartos.get(2).setEquipoLocalTorneo(et(indexEt, grB.get(0)));
            cuartos.get(2).setEquipoVisitanteTorneo(et(indexEt, grA.get(3)));
            cuartos.get(3).setEquipoLocalTorneo(et(indexEt, grB.get(1)));
            cuartos.get(3).setEquipoVisitanteTorneo(et(indexEt, grA.get(2)));
        }
    }

    private List<Partido> ordenadosPorFecha(List<Partido> partidos, FaseTorneo fase) {
        return partidos.stream()
                .filter(p -> p.getFase() == fase)
                .sorted(Comparator.comparing(Partido::getFecha,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private EquipoTorneo et(Map<UUID, EquipoTorneo> idx, PosicionGrupoDTO p) {
        return idx.get(p.getEquipoTorneoId());
    }
}

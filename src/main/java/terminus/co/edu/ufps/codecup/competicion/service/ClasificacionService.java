package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.PosicionGrupoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EventoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.TipoEvento;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Calcula tablas de posiciones aplicando los criterios de desempate del reglamento:
 *  (1) puntos                           (en realidad ya implícito en el orden)
 *  (2) ganador del partido h2h          (puntos entre los empatados)
 *  (3) DG en h2h                        (diferencia de goles entre empatados)
 *  (4) GF global
 *  (5) GC global (menos es mejor)
 *  (6) Rojas global (menos es mejor)
 *  (7) UUID (orden estable, reemplaza al sorteo)
 *
 * Partidos en estado DESCANSO se ignoran (no se jugaron porque un equipo fue expulsado).
 * Partidos WO suman como ganados/perdidos con goles ficticios (3-0) que ya quedan
 * registrados como eventos GOL en la BD, así que no necesitan caso especial.
 */
@Service
@RequiredArgsConstructor
public class ClasificacionService {

    private final TorneoRepository torneoRepo;
    private final PartidoRepository partidoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final EventoPartidoRepository eventoRepo;

    /** Devuelve clasificaciones por grupo. Para LIGA usa la key "GLOBAL". */
    @Transactional(readOnly = true)
    public Map<String, List<PosicionGrupoDTO>> calcularPorGrupo(UUID torneoId) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        List<EquipoTorneo> equipos = equipoTorneoRepo
                .findByTorneoIdOrderByFechaInscripcionAsc(torneoId);
        List<Partido> partidosGrupos = partidoRepo.findByTorneoId(torneoId).stream()
                .filter(p -> p.getFase() == FaseTorneo.GRUPOS || p.getFase() == null)
                .toList();

        // Agrupar equipos por letra; null/vacío → "GLOBAL" (formato LIGA)
        TreeMap<String, List<EquipoTorneo>> equiposPorGrupo = new TreeMap<>();
        for (EquipoTorneo et : equipos) {
            if (et.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) continue;
            String grupo = letraGrupo(partidosGrupos, et);
            equiposPorGrupo.computeIfAbsent(grupo, k -> new ArrayList<>()).add(et);
        }

        Map<String, List<PosicionGrupoDTO>> resultado = new LinkedHashMap<>();
        for (var entry : equiposPorGrupo.entrySet()) {
            String grupo = entry.getKey();
            List<EquipoTorneo> miembros = entry.getValue();
            List<EquipoStats> stats = new ArrayList<>();
            for (EquipoTorneo et : miembros) {
                stats.add(calcularStats(et, partidosGrupos, miembros));
            }
            stats.sort(comparator(stats));
            List<PosicionGrupoDTO> dtos = new ArrayList<>();
            for (int i = 0; i < stats.size(); i++) {
                dtos.add(stats.get(i).toDTO(i + 1, grupo));
            }
            resultado.put(grupo, dtos);
        }
        return resultado;
    }

    /** True si todos los partidos de fase GRUPOS están cerrados (FINALIZADO/WO/DESCANSO). */
    @Transactional(readOnly = true)
    public boolean faseGruposCompleta(UUID torneoId) {
        var grupos = partidoRepo.findByTorneoId(torneoId).stream()
                .filter(p -> p.getFase() == FaseTorneo.GRUPOS)
                .toList();
        if (grupos.isEmpty()) return false;
        for (Partido p : grupos) {
            EstadoPartido e = p.getEstado();
            if (e == EstadoPartido.PROGRAMADO || e == EstadoPartido.EN_CURSO
                    || e == EstadoPartido.APLAZADO) {
                return false;
            }
        }
        return true;
    }

    /** Letra del grupo de un equipo: se infiere del primer partido GRUPOS donde participa. */
    private String letraGrupo(List<Partido> partidos, EquipoTorneo et) {
        for (Partido p : partidos) {
            if (esLocal(p, et) || esVisitante(p, et)) {
                String g = p.getGrupo();
                return (g == null || g.isBlank()) ? "GLOBAL" : g;
            }
        }
        return "GLOBAL";
    }

    private EquipoStats calcularStats(EquipoTorneo et, List<Partido> partidos, List<EquipoTorneo> miembrosGrupo) {
        EquipoStats s = new EquipoStats();
        s.equipoTorneo = et;

        Map<UUID, int[]> h2h = new HashMap<>(); // [pts, gf, gc]
        for (EquipoTorneo otro : miembrosGrupo) {
            if (!otro.getId().equals(et.getId())) {
                h2h.put(otro.getId(), new int[]{0, 0, 0});
            }
        }

        // Para form ordenado por fecha
        List<Object[]> resultadosConFecha = new ArrayList<>();

        for (Partido p : partidos) {
            boolean local = esLocal(p, et);
            boolean vis = esVisitante(p, et);
            if (!local && !vis) continue;
            if (p.getEstado() == EstadoPartido.DESCANSO) continue;
            if (p.getEstado() != EstadoPartido.FINALIZADO && p.getEstado() != EstadoPartido.WO) continue;
            if ("SIN_PAGO_ARBITRAJE".equals(p.getTipoCierre())) {
                s.pj++;
                continue;
            }

            UUID localId = p.getEquipoLocalTorneo() != null ? p.getEquipoLocalTorneo().getId() : null;
            UUID visId = p.getEquipoVisitanteTorneo() != null ? p.getEquipoVisitanteTorneo().getId() : null;
            long gLocal = localId == null ? 0 :
                    eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(p.getId(), localId, TipoEvento.GOL);
            long gVis = visId == null ? 0 :
                    eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(p.getId(), visId, TipoEvento.GOL);
            int aFavor = (int) (local ? gLocal : gVis);
            int enContra = (int) (local ? gVis : gLocal);

            s.pj++;
            s.gf += aFavor;
            s.gc += enContra;
            String marca;
            if (aFavor > enContra) { s.pg++; s.pts += 3; marca = "W"; }
            else if (aFavor < enContra) { s.pp++; marca = "L"; }
            else { s.pe++; s.pts += 1; marca = "D"; }
            resultadosConFecha.add(new Object[]{p.getFecha(), marca});

            // Rojas del equipo en este partido
            List<EventoPartido> eventos = eventoRepo.findByPartidoIdOrderByOrdenAsc(p.getId());
            for (EventoPartido ev : eventos) {
                if (ev.getTipoEvento() == TipoEvento.ROJA
                        && ev.getEquipoTorneo() != null
                        && ev.getEquipoTorneo().getId().equals(et.getId())) {
                    s.rojas++;
                }
            }

            // H2H solo si el rival pertenece al mismo grupo
            UUID rivalId = local ? visId : localId;
            if (rivalId != null && h2h.containsKey(rivalId)) {
                int[] cur = h2h.get(rivalId);
                if (aFavor > enContra) cur[0] += 3;
                else if (aFavor == enContra) cur[0] += 1;
                cur[1] += aFavor;
                cur[2] += enContra;
            }
        }

        s.h2h = h2h;
        s.descalificado = et.getEstadoInscripcion() == EstadoInscripcion.EXPULSADO;

        // form: últimos 3 partidos por fecha, más reciente primero
        resultadosConFecha.sort((a, b) -> {
            java.time.LocalDateTime fa = (java.time.LocalDateTime) a[0];
            java.time.LocalDateTime fb = (java.time.LocalDateTime) b[0];
            if (fa == null && fb == null) return 0;
            if (fa == null) return -1;
            if (fb == null) return 1;
            return fa.compareTo(fb);
        });
        List<String> form = new ArrayList<>();
        int desde = Math.max(0, resultadosConFecha.size() - 3);
        for (int i = resultadosConFecha.size() - 1; i >= desde; i--) {
            form.add((String) resultadosConFecha.get(i)[1]);
        }
        s.form = form;
        return s;
    }

    private boolean esLocal(Partido p, EquipoTorneo et) {
        return p.getEquipoLocalTorneo() != null
                && p.getEquipoLocalTorneo().getId().equals(et.getId());
    }
    private boolean esVisitante(Partido p, EquipoTorneo et) {
        return p.getEquipoVisitanteTorneo() != null
                && p.getEquipoVisitanteTorneo().getId().equals(et.getId());
    }

    /**
     * Comparator encadenado con criterios de desempate.
     * h2h se evalúa solo entre equipos que comparten puntos.
     */
    private Comparator<EquipoStats> comparator(List<EquipoStats> all) {
        return (a, b) -> {
            // Descalificados van al final
            if (Boolean.TRUE.equals(a.descalificado) != Boolean.TRUE.equals(b.descalificado)) {
                return Boolean.TRUE.equals(a.descalificado) ? 1 : -1;
            }
            int byPts = Integer.compare(b.pts, a.pts);
            if (byPts != 0) return byPts;
            // h2h points entre los empatados en pts
            int byH2hPts = Integer.compare(h2hPts(b, a), h2hPts(a, b));
            if (byH2hPts != 0) return byH2hPts;
            // h2h GD entre los empatados
            int byH2hGd = Integer.compare(h2hGd(b), h2hGd(a));
            if (byH2hGd != 0) return byH2hGd;
            // GF global
            int byGf = Integer.compare(b.gf, a.gf);
            if (byGf != 0) return byGf;
            // GC global (menos)
            int byGc = Integer.compare(a.gc, b.gc);
            if (byGc != 0) return byGc;
            // Rojas (menos)
            int byRojas = Integer.compare(a.rojas, b.rojas);
            if (byRojas != 0) return byRojas;
            // Orden estable por UUID
            return a.equipoTorneo.getId().toString().compareTo(b.equipoTorneo.getId().toString());
        };
    }

    /** Puntos que a sumó frente a b (en h2h restringido al grupo). */
    private int h2hPts(EquipoStats a, EquipoStats b) {
        int[] cur = a.h2h.get(b.equipoTorneo.getId());
        return cur == null ? 0 : cur[0];
    }

    /** Diferencia de goles de a en h2h contra rivales empatados (aproximación: el rival b). */
    private int h2hGd(EquipoStats a) {
        int gd = 0;
        for (int[] cur : a.h2h.values()) {
            gd += cur[1] - cur[2];
        }
        return gd;
    }

    // ── helper class ──────────────────────────────────────────
    private static class EquipoStats {
        EquipoTorneo equipoTorneo;
        int pts, pj, pg, pe, pp, gf, gc, rojas;
        Map<UUID, int[]> h2h = new HashMap<>();
        List<String> form = new ArrayList<>();
        Boolean descalificado;

        PosicionGrupoDTO toDTO(int posicion, String grupo) {
            return PosicionGrupoDTO.builder()
                    .posicion(posicion)
                    .equipoTorneoId(equipoTorneo.getId())
                    .equipoNombre(equipoTorneo.getEquipo().getNombre())
                    .grupo(grupo)
                    .pts(pts).pj(pj).pg(pg).pe(pe).pp(pp).gf(gf).gc(gc)
                    .dg(gf - gc).rojas(rojas)
                    .form(form)
                    .descalificado(descalificado)
                    .build();
        }
    }
}

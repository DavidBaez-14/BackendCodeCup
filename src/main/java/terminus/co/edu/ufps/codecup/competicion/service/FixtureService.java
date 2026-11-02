package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.SlotPartido;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FixtureService {

    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final PartidoRepository partidoRepo;

    /**
     * Dispatcher: genera el fixture segun el formato configurado del torneo.
     * Falla si el torneo no esta EN_CURSO, si no tiene formato, o si ya existen partidos.
     */
    @Transactional
    public List<Partido> generar(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.EN_CURSO) {
            throw new RuntimeException("El torneo debe estar EN_CURSO para generar fixture.");
        }
        if (torneo.getFormato() == null) {
            throw new RuntimeException("El torneo no tiene formato configurado.");
        }
        if (partidoRepo.existsByTorneoId(torneoId)) {
            throw new RuntimeException("El torneo ya tiene fixture generado.");
        }

        var aprobados = equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                torneoId, EstadoInscripcion.APROBADO);
        if (aprobados.size() < 2) {
            throw new RuntimeException("Se necesitan al menos 2 equipos APROBADOS para generar fixture.");
        }

        return switch (torneo.getFormato()) {
            case LIGA -> generarLiga(torneo, aprobados);
            case ELIMINACION_DIRECTA -> generarEliminacionDirecta(torneo, aprobados);
            case GRUPOS_ELIMINATORIAS -> generarGruposPlusPlayoff(torneo, aprobados, false);
            case CHAMPIONS -> generarGruposPlusPlayoff(torneo, aprobados, true);
        };
    }

    // ── LIGA (round-robin clasico) ───────────────────────────────

    private List<Partido> generarLiga(Torneo torneo, List<EquipoTorneo> aprobados) {
        var jornadas = roundRobin(aprobados);
        var base = baseProgramacion(torneo);
        var creados = new ArrayList<Partido>();

        for (int j = 0; j < jornadas.size(); j++) {
            List<Partido> ronda = jornadas.get(j);
            for (int matchIdx = 0; matchIdx < ronda.size(); matchIdx++) {
                var partido = ronda.get(matchIdx);
                partido.setTorneo(torneo);
                partido.setFecha(base.plusDays(7L * j).plusHours(matchIdx * 2L));
                partido.setEstado(EstadoPartido.PROGRAMADO);
                partido.setFase(FaseTorneo.GRUPOS);
                partido.setJornada(j + 1);
                partido.setGrupo(null);
                partidoRepo.save(partido);
                creados.add(partido);
            }
        }
        return creados;
    }

    // ── ELIMINACION DIRECTA (bracket KO) ──────────────────────────

    private List<Partido> generarEliminacionDirecta(Torneo torneo, List<EquipoTorneo> aprobados) {
        List<FaseTorneo> rondas = parseRondas(torneo);
        if (rondas.isEmpty()) {
            throw new RuntimeException("El torneo no tiene rondas eliminatorias configuradas.");
        }
        // La primera ronda determina cuantos partidos hay en la base del bracket.
        FaseTorneo primera = rondas.get(0);
        int partidosPrimera = partidosEnRonda(primera);
        int cupos = partidosPrimera * 2;

        if (aprobados.size() > cupos) {
            throw new RuntimeException(
                "Hay " + aprobados.size() + " equipos aprobados pero la primera ronda ("
                + primera + ") solo tiene " + cupos + " cupos.");
        }

        // Sembrar equipos (orden de inscripcion); slots vacios = BYE
        List<EquipoTorneo> seed = new ArrayList<>(aprobados);
        while (seed.size() < cupos) seed.add(null);

        var base = baseProgramacion(torneo);
        List<List<Partido>> persistidosPorRonda = new ArrayList<>();
        var todos = new ArrayList<Partido>();

        // Crear partidos de cada ronda (las posteriores con placeholders nulls)
        for (int rIdx = 0; rIdx < rondas.size(); rIdx++) {
            FaseTorneo fase = rondas.get(rIdx);
            int count = partidosEnRonda(fase);
            var ronda = new ArrayList<Partido>();
            for (int m = 0; m < count; m++) {
                Partido p = Partido.builder()
                        .torneo(torneo)
                        .fecha(base.plusDays(7L * rIdx).plusHours(m * 2L))
                        .estado(EstadoPartido.PROGRAMADO)
                        .fase(fase)
                        .jornada(rIdx + 1)
                        .grupo(null)
                        .build();
                if (rIdx == 0) {
                    p.setEquipoLocalTorneo(seed.get(m * 2));
                    p.setEquipoVisitanteTorneo(seed.get(m * 2 + 1));
                }
                partidoRepo.save(p);
                ronda.add(p);
                todos.add(p);
            }
            persistidosPorRonda.add(ronda);
        }

        // Encadenar cada partido con su siguiente (el ganador llena LOCAL/VISITANTE
        // del partido index/2 en la ronda siguiente)
        for (int rIdx = 0; rIdx < persistidosPorRonda.size() - 1; rIdx++) {
            var ronda = persistidosPorRonda.get(rIdx);
            var siguiente = persistidosPorRonda.get(rIdx + 1);
            for (int m = 0; m < ronda.size(); m++) {
                Partido p = ronda.get(m);
                Partido next = siguiente.get(m / 2);
                p.setSiguientePartido(next);
                p.setSiguienteSlot(m % 2 == 0 ? SlotPartido.LOCAL : SlotPartido.VISITANTE);
                partidoRepo.save(p);
            }
        }

        // Resolver BYEs de la primera ronda: si un slot vino vacio, el otro
        // pasa automaticamente al siguiente partido.
        for (Partido p : persistidosPorRonda.get(0)) {
            if (p.getEquipoLocalTorneo() != null && p.getEquipoVisitanteTorneo() == null
                    && p.getSiguientePartido() != null) {
                avanzarGanador(p, p.getEquipoLocalTorneo());
                p.setEstado(EstadoPartido.DESCANSO);
                partidoRepo.save(p);
            } else if (p.getEquipoVisitanteTorneo() != null && p.getEquipoLocalTorneo() == null
                    && p.getSiguientePartido() != null) {
                avanzarGanador(p, p.getEquipoVisitanteTorneo());
                p.setEstado(EstadoPartido.DESCANSO);
                partidoRepo.save(p);
            }
        }

        return todos;
    }

    // ── GRUPOS + ELIMINATORIAS / CHAMPIONS ───────────────────────

    private List<Partido> generarGruposPlusPlayoff(Torneo torneo, List<EquipoTorneo> aprobados, boolean conRepechaje) {
        if (torneo.getNumGrupos() == null || torneo.getNumGrupos() < 2) {
            throw new RuntimeException("Define al menos 2 grupos.");
        }
        int g = torneo.getNumGrupos();
        if (aprobados.size() < g * 2) {
            throw new RuntimeException(
                "Se necesitan al menos " + (g * 2) + " equipos APROBADOS para " + g + " grupos.");
        }

        // Reparticion round-robin de equipos en grupos (snake seeding simple)
        List<List<EquipoTorneo>> grupos = new ArrayList<>();
        for (int i = 0; i < g; i++) grupos.add(new ArrayList<>());
        for (int i = 0; i < aprobados.size(); i++) {
            grupos.get(i % g).add(aprobados.get(i));
        }

        var base = baseProgramacion(torneo);
        var todos = new ArrayList<Partido>();

        // Partidos de fase de grupos: round-robin por grupo
        int diaCounter = 0;
        for (int idxGrupo = 0; idxGrupo < grupos.size(); idxGrupo++) {
            String letra = String.valueOf((char) ('A' + idxGrupo));
            var jornadas = roundRobin(grupos.get(idxGrupo));
            for (int j = 0; j < jornadas.size(); j++) {
                for (int matchIdx = 0; matchIdx < jornadas.get(j).size(); matchIdx++) {
                    Partido p = jornadas.get(j).get(matchIdx);
                    p.setTorneo(torneo);
                    p.setFecha(base.plusDays(diaCounter + j).plusHours(11 + matchIdx * 2L));
                    p.setEstado(EstadoPartido.PROGRAMADO);
                    p.setFase(FaseTorneo.GRUPOS);
                    p.setJornada(j + 1);
                    p.setGrupo(letra);
                    partidoRepo.save(p);
                    todos.add(p);
                }
            }
            diaCounter += jornadas.size();
        }

        // Fases eliminatorias: placeholders encadenados
        List<FaseTorneo> rondasKo = new ArrayList<>();
        if (conRepechaje && Boolean.TRUE.equals(torneo.getRepechaje())) {
            rondasKo.add(FaseTorneo.REPECHAJE);
        }
        rondasKo.addAll(parseRondas(torneo));

        if (rondasKo.isEmpty()) return todos;

        LocalDateTime baseKo = base.plusDays(diaCounter + 1);
        List<List<Partido>> persistidosPorRonda = new ArrayList<>();
        for (int rIdx = 0; rIdx < rondasKo.size(); rIdx++) {
            FaseTorneo fase = rondasKo.get(rIdx);
            int count = partidosEnRonda(fase);
            var ronda = new ArrayList<Partido>();
            for (int m = 0; m < count; m++) {
                Partido p = Partido.builder()
                        .torneo(torneo)
                        .fecha(baseKo.plusDays(7L * rIdx).plusHours(m * 2L))
                        .estado(EstadoPartido.PROGRAMADO)
                        .fase(fase)
                        .jornada(rIdx + 1)
                        .grupo(null)
                        .build();
                partidoRepo.save(p);
                ronda.add(p);
                todos.add(p);
            }
            persistidosPorRonda.add(ronda);
        }
        for (int rIdx = 0; rIdx < persistidosPorRonda.size() - 1; rIdx++) {
            var ronda = persistidosPorRonda.get(rIdx);
            var siguiente = persistidosPorRonda.get(rIdx + 1);
            for (int m = 0; m < ronda.size(); m++) {
                if (m / 2 >= siguiente.size()) continue;
                Partido p = ronda.get(m);
                Partido next = siguiente.get(m / 2);
                p.setSiguientePartido(next);
                p.setSiguienteSlot(m % 2 == 0 ? SlotPartido.LOCAL : SlotPartido.VISITANTE);
                partidoRepo.save(p);
            }
        }

        return todos;
    }

    // ── Avance de ganador (llamado desde PartidoAdminService) ─────

    /**
     * Coloca al ganador en el slot apropiado del siguiente partido del bracket.
     * Si ya estaba colocado, no hace nada (idempotente).
     */
    @Transactional
    public void avanzarGanador(Partido partido, EquipoTorneo ganador) {
        Partido siguiente = partido.getSiguientePartido();
        if (siguiente == null || ganador == null) return;
        SlotPartido slot = partido.getSiguienteSlot();
        if (slot == SlotPartido.LOCAL) {
            if (siguiente.getEquipoLocalTorneo() == null) {
                siguiente.setEquipoLocalTorneo(ganador);
                partidoRepo.save(siguiente);
            }
        } else if (slot == SlotPartido.VISITANTE) {
            if (siguiente.getEquipoVisitanteTorneo() == null) {
                siguiente.setEquipoVisitanteTorneo(ganador);
                partidoRepo.save(siguiente);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private LocalDateTime baseProgramacion(Torneo t) {
        // Si el torneo tiene fecha_inicio configurada la usamos; si no, mañana 15:00.
        LocalDateTime base = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0)
                .plusDays(1)
                .withHour(15);
        if (t.getFechaInicio() != null) {
            base = t.getFechaInicio().atTime(15, 0);
        }
        return base;
    }

    private int partidosEnRonda(FaseTorneo fase) {
        return switch (fase) {
            case OCTAVOS -> 8;
            case CUARTOS -> 4;
            case SEMIS -> 2;
            case FINAL, TERCER_PUESTO -> 1;
            case REPECHAJE -> 4;
            case GRUPOS -> 0;
        };
    }

    private List<FaseTorneo> parseRondas(Torneo torneo) {
        if (torneo.getRondasPlayoff() == null || torneo.getRondasPlayoff().isBlank()) {
            return List.of();
        }
        // Mantener orden lógico aunque vengan desordenadas
        List<FaseTorneo> orden = List.of(
                FaseTorneo.OCTAVOS, FaseTorneo.CUARTOS, FaseTorneo.SEMIS,
                FaseTorneo.FINAL, FaseTorneo.TERCER_PUESTO);
        List<FaseTorneo> seleccionadas = Arrays.stream(torneo.getRondasPlayoff().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(FaseTorneo::valueOf)
                .toList();
        var resultado = new ArrayList<FaseTorneo>();
        for (FaseTorneo f : orden) {
            if (seleccionadas.contains(f) && f != FaseTorneo.TERCER_PUESTO) {
                resultado.add(f);
            }
        }
        // TERCER_PUESTO se agrega al final si esta seleccionado (no encadena con FINAL)
        if (seleccionadas.contains(FaseTorneo.TERCER_PUESTO)) {
            resultado.add(FaseTorneo.TERCER_PUESTO);
        }
        return resultado;
    }

    /**
     * Algoritmo Berger (round-robin). Para n impar agrega "bye" (null).
     * Devuelve lista de jornadas; cada jornada es lista de Partidos con los EquipoTorneo asignados (sin persistir).
     */
    private List<List<Partido>> roundRobin(List<EquipoTorneo> equipos) {
        List<EquipoTorneo> work = new ArrayList<>(equipos);
        if (work.size() % 2 != 0) {
            work.add(null); // bye
        }
        int n = work.size();
        int rondas = n - 1;
        int mitad = n / 2;
        List<List<Partido>> jornadas = new ArrayList<>();

        List<EquipoTorneo> rot = new ArrayList<>(work);
        for (int r = 0; r < rondas; r++) {
            List<Partido> ronda = new ArrayList<>();
            for (int i = 0; i < mitad; i++) {
                var local = rot.get(i);
                var visitante = rot.get(n - 1 - i);
                if (local == null || visitante == null) {
                    continue;
                }
                var p = (i == 0 && r % 2 == 1)
                        ? Partido.builder().equipoLocalTorneo(visitante).equipoVisitanteTorneo(local).build()
                        : Partido.builder().equipoLocalTorneo(local).equipoVisitanteTorneo(visitante).build();
                ronda.add(p);
            }
            jornadas.add(ronda);
            var fijo = rot.get(0);
            var ultimo = rot.remove(rot.size() - 1);
            rot.add(1, ultimo);
            rot.set(0, fijo);
        }
        return jornadas;
    }

}

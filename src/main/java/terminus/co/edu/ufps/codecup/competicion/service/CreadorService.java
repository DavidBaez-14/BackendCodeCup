package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.CreadorTorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;

/** La sección Creador (spec 0024): los torneos que la cuenta administra y lo que espera su decisión. */
@Service
@RequiredArgsConstructor
public class CreadorService {

    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final ComprobanteRepository comprobanteRepo;
    private final PermisosTorneo permisos;
    private final TorneoCreacionService torneoCreacionService;

    /** Resumen para "Tus secciones" en Inicio (spec 0026). */
    public record Resumen(int torneos, int porRevisar) {
    }

    /** Sin terminar primero; dentro de cada grupo, los más nuevos primero. */
    @Transactional(readOnly = true)
    public List<CreadorTorneoDTO> misTorneos(UUID cuentaId) {
        var torneos = torneoRepo.findAllById(permisos.torneosQueAdministra(cuentaId));
        var comprobantesPorTorneo = comprobantesPendientesPorTorneo(new HashSet<>(torneos.stream().map(Torneo::getId).toList()), false);
        return torneos.stream()
                .sorted(Comparator.comparing((Torneo t) -> t.getEstado() == EstadoTorneo.FINALIZADO)
                        .thenComparing(Torneo::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toDTO(t, comprobantesPorTorneo.getOrDefault(t.getId(), 0)))
                .toList();
    }

    /**
     * {@code porRevisar} = inscripciones PENDIENTE_PAGO + comprobantes de multa por revisar. Los
     * comprobantes de inscripción no se suman aparte: se revisan junto con su inscripción.
     */
    @Transactional(readOnly = true)
    public Resumen resumen(UUID cuentaId) {
        var ids = permisos.torneosQueAdministra(cuentaId);
        if (ids.isEmpty()) return new Resumen(0, 0);
        int inscripciones = ids.stream().mapToInt(this::inscripcionesPendientes).sum();
        int multas = comprobantesPendientesPorTorneo(new HashSet<>(ids), true).values().stream()
                .mapToInt(Integer::intValue).sum();
        return new Resumen(ids.size(), inscripciones + multas);
    }

    private CreadorTorneoDTO toDTO(Torneo t, int comprobantes) {
        int aprobados = equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                t.getId(), EstadoInscripcion.APROBADO).size();
        return CreadorTorneoDTO.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .edicion(t.getEdicion())
                .estado(t.getEstado() != null ? t.getEstado().name() : null)
                .formato(t.getFormato() != null ? t.getFormato().name() : null)
                .fechaInicio(t.getFechaInicio())
                .fechaFin(t.getFechaFin())
                .deporte(t.getDeporte())
                .visibilidad(t.getVisibilidad() != null ? t.getVisibilidad().name() : null)
                .cupos(t.getCupos())
                .enlace(torneoCreacionService.enlace(t))
                .inscritos(aprobados)
                .pendientes(new CreadorTorneoDTO.Pendientes(inscripcionesPendientes(t.getId()), comprobantes))
                .creadoEn(t.getCreatedAt())
                .build();
    }

    private int inscripcionesPendientes(UUID torneoId) {
        return equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                torneoId, EstadoInscripcion.PENDIENTE_PAGO).size();
    }

    /** Comprobantes por revisar de esos torneos, contados por torneo. */
    private Map<UUID, Integer> comprobantesPendientesPorTorneo(Set<UUID> torneos, boolean soloMultas) {
        Map<UUID, Integer> conteo = new HashMap<>();
        if (torneos.isEmpty()) return conteo;
        var pendientes = soloMultas
                ? comprobanteRepo.findByTipoAndEstado(TipoComprobante.MULTA, EstadoComprobante.PENDIENTE_REVISION)
                : comprobanteRepo.findByEstado(EstadoComprobante.PENDIENTE_REVISION);
        pendientes.stream()
                .map(permisos::torneoDeComprobante)
                .filter(Objects::nonNull)
                .filter(torneos::contains)
                .forEach(id -> conteo.merge(id, 1, Integer::sum));
        return conteo;
    }
}

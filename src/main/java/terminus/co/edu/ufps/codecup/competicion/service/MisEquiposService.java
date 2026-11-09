package terminus.co.edu.ufps.codecup.competicion.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.jugador.MiEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;

/** "Mis equipos" (spec 0026, MOV-0004): donde la persona juega o delega, en cualquier torneo. */
@Service
@RequiredArgsConstructor
public class MisEquiposService {

    private static final List<EstadoPartido> POR_JUGAR =
            List.of(EstadoPartido.PROGRAMADO, EstadoPartido.EN_CURSO, EstadoPartido.APLAZADO);

    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final PartidoRepository partidoRepo;
    private final ComprobanteRepository comprobanteRepo;

    /**
     * Las inscripciones que delega (en cualquier estado, para que vea si la rechazaron) y los
     * planteles donde está activo. Primero los torneos sin terminar, por el próximo partido, y
     * después los más recientes.
     */
    @Transactional(readOnly = true)
    public List<MiEquipoDTO> misEquipos(UUID personaId) {
        Map<UUID, MiEquipoDTO> porInscripcion = new LinkedHashMap<>();
        for (EquipoTorneo et : equipoTorneoRepo.findByDelegadoPersonaIdOrderByFechaInscripcionDesc(personaId)) {
            var camiseta = jugadorEquipoRepo.findByPersonaIdAndEquipoTorneoId(personaId, et.getId())
                    .map(JugadorEquipo::getNumeroCamiseta).orElse(null);
            porInscripcion.put(et.getId(), toDTO(et, "DELEGADO", camiseta, comprobanteDe(et)));
        }
        for (JugadorEquipo je : jugadorEquipoRepo.findByPersonaIdOrderByFechaInicioDesc(personaId)) {
            if (je.getEstado() != EstadoMembresia.ACTIVO) continue;
            var et = je.getEquipoTorneo();
            porInscripcion.putIfAbsent(et.getId(), toDTO(et, "JUGADOR", je.getNumeroCamiseta(), null));
        }
        return porInscripcion.values().stream()
                .sorted(Comparator
                        .comparing((MiEquipoDTO d) -> EstadoTorneo.FINALIZADO.name().equals(d.getEstadoTorneo()))
                        .thenComparing(d -> d.getProximoPartido() != null ? d.getProximoPartido().fechaHora() : null,
                                Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
                .toList();
    }

    private MiEquipoDTO toDTO(EquipoTorneo et, String rol, Integer camiseta, String comprobante) {
        var torneo = et.getTorneo();
        return MiEquipoDTO.builder()
                .equipoTorneoId(et.getId())
                .equipoId(et.getEquipo().getId())
                .equipo(et.getEquipo().getNombre())
                .torneoId(torneo.getId())
                .torneo(torneo.getNombre())
                .deporte(torneo.getDeporte())
                .estadoTorneo(torneo.getEstado() != null ? torneo.getEstado().name() : null)
                .rol(rol)
                .estadoInscripcion(et.getEstadoInscripcion().name())
                .motivoRechazo(et.getMotivoRechazo())
                .comprobante(comprobante)
                .numeroCamiseta(camiseta)
                .proximoPartido(proximoPartido(et))
                .build();
    }

    private String comprobanteDe(EquipoTorneo et) {
        return comprobanteRepo.findByReferenciaId(et.getId()).stream()
                .findFirst()
                .map(c -> c.getEstado().name())
                .orElse(null);
    }

    private MiEquipoDTO.ProximoPartido proximoPartido(EquipoTorneo et) {
        return partidoRepo.findByEquipoTorneoYEstadoIn(et.getTorneo().getId(), et.getId(), POR_JUGAR).stream()
                .min(Comparator.comparing(Partido::getFecha))
                .map(p -> {
                    boolean local = p.getEquipoLocalTorneo() != null && p.getEquipoLocalTorneo().getId().equals(et.getId());
                    var rival = local ? p.getEquipoVisitanteTorneo() : p.getEquipoLocalTorneo();
                    return new MiEquipoDTO.ProximoPartido(p.getId(),
                            rival != null ? rival.getEquipo().getNombre() : null,
                            local, p.getFecha(), p.getLugar(), p.getEstado().name());
                })
                .orElse(null);
    }
}

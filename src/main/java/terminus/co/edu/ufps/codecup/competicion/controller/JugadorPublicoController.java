package terminus.co.edu.ufps.codecup.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.dto.jugador.EquipoEnTorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/jugador")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JUGADOR')")
public class JugadorPublicoController {

    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;

    @GetMapping("/torneos/{torneoId}/equipos")
    public ResponseEntity<List<EquipoEnTorneoDTO>> equiposPorTorneo(@PathVariable UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO && torneo.getEstado() != EstadoTorneo.EN_CURSO) {
            throw new RuntimeException("El torneo no esta aceptando jugadores.");
        }
        var equipos = equipoTorneoRepo.findByTorneoIdOrderByFechaInscripcionAsc(torneoId)
                .stream()
                .filter(et -> et.getEstadoInscripcion() != EstadoInscripcion.RECHAZADO)
                .map(et -> EquipoEnTorneoDTO.builder()
                        .equipoTorneoId(et.getId())
                        .equipoId(et.getEquipo().getId())
                        .equipoNombre(et.getEquipo().getNombre())
                        .delegadoCedula(et.getDelegadoCedula())
                        .estadoInscripcion(et.getEstadoInscripcion().name())
                        .miembros(jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(et.getId()).size())
                        .build())
                .toList();
        return ResponseEntity.ok(equipos);
    }
}

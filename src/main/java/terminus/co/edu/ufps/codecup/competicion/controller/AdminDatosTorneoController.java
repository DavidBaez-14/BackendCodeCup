package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.TorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.ActualizarDatosTorneoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.TorneoCreadoDTO;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.service.TorneoCreacionService;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/** Datos básicos, visibilidad y enlace de un torneo (spec 0029). Solo para sus admins. */
@RestController
@RequestMapping("/api/supercopa/admin/torneos/{torneoId}")
@RequiredArgsConstructor
@Tag(name = "Creador")
public class AdminDatosTorneoController {

    private final TorneoCreacionService torneoCreacionService;
    private final TorneoRepository torneoRepository;

    /** Nombre, deporte, visibilidad, cupos, fechas, cierre y reglas. Lo que llega en null no cambia. */
    @PutMapping("/datos")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoDTO> actualizar(@PathVariable UUID torneoId, @RequestBody ActualizarDatosTorneoRequest req) {
        return ResponseEntity.ok(torneoCreacionService.actualizarDatos(torneoId, req));
    }

    /** El enlace para compartir (también el de un torneo privado: solo sus admins lo piden). */
    @GetMapping("/enlace")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoCreadoDTO> enlace(@PathVariable UUID torneoId) {
        var t = torneoRepository.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        return ResponseEntity.ok(TorneoCreadoDTO.builder().id(t.getId()).nombre(t.getNombre())
                .visibilidad(t.getVisibilidad().name())
                .enlaceToken(t.getEnlaceToken()).enlace(torneoCreacionService.enlace(t)).build());
    }

    /** Un enlace nuevo: el anterior deja de abrir el torneo. */
    @PostMapping("/enlace/regenerar")
    @PreAuthorize("@authz.adminDeTorneo(#torneoId)")
    public ResponseEntity<TorneoCreadoDTO> regenerar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoCreacionService.regenerarEnlace(torneoId));
    }
}

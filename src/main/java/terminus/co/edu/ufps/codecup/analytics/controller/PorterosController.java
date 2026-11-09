package terminus.co.edu.ufps.codecup.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.analytics.dto.PorteroDTO;
import terminus.co.edu.ufps.codecup.analytics.service.PorterosService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/torneos")
@RequiredArgsConstructor
public class PorterosController {

    /** Spec 0029: un torneo PRIVADO o en borrador no se ve por id si no participas. */
    private final terminus.co.edu.ufps.codecup.competicion.service.VisibilidadService visibilidad;

    private final PorterosService service;

    @GetMapping("/{torneoId}/porteros")
    public ResponseEntity<List<PorteroDTO>> porteros(
            @PathVariable UUID torneoId,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        visibilidad.exigirVisiblePorId(torneoId);
        return ResponseEntity.ok(service.obtener(torneoId, limit));
    }
}

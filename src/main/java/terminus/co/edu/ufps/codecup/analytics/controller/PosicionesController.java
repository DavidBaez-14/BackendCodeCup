package terminus.co.edu.ufps.codecup.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.analytics.dto.PosicionDTO;
import terminus.co.edu.ufps.codecup.analytics.service.PosicionesService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/torneos")
@RequiredArgsConstructor
public class PosicionesController {

    private final PosicionesService service;

    @GetMapping("/{torneoId}/posiciones")
    public ResponseEntity<Map<String, List<PosicionDTO>>> posiciones(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(service.obtenerPorGrupo(torneoId));
    }
}

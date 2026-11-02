package terminus.co.edu.ufps.codecup.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.analytics.dto.GoleadorDTO;
import terminus.co.edu.ufps.codecup.analytics.service.GoleadoresService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/torneos")
@RequiredArgsConstructor
public class GoleadoresController {

    private final GoleadoresService service;

    @GetMapping("/{torneoId}/goleadores")
    public ResponseEntity<List<GoleadorDTO>> goleadores(
            @PathVariable UUID torneoId,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.obtener(torneoId, limit));
    }
}

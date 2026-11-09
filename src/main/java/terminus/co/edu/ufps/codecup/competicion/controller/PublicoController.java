package terminus.co.edu.ufps.codecup.competicion.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.DeporteDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.PaginaDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.TorneoPublicoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.TorneoResumenPublicoDTO;
import terminus.co.edu.ufps.codecup.competicion.service.TorneoPublicoService;

/**
 * Lecturas sin sesión (spec 0029, MOV-0005): Explorar y el detalle de un torneo. Con un token
 * válido, el detalle suma {@code miRelacion}. Un torneo que no existe o no se puede ver responde
 * igual (404), para no revelar torneos privados.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Público")
public class PublicoController {

    private final TorneoPublicoService torneoPublicoService;

    @GetMapping("/deportes")
    public ResponseEntity<List<DeporteDTO>> deportes() {
        return ResponseEntity.ok(torneoPublicoService.deportes());
    }

    /** Solo torneos PUBLICO. {@code q} sin distinguir mayúsculas ni tildes. */
    @GetMapping("/torneos")
    public ResponseEntity<PaginaDTO<TorneoResumenPublicoDTO>> torneos(
            @RequestParam(required = false) String deporte,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(torneoPublicoService.explorar(deporte, q, page, size));
    }

    @GetMapping("/torneos/{torneoId}")
    public ResponseEntity<TorneoPublicoDTO> torneo(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoPublicoService.detalle(torneoId));
    }

    /** Por el enlace de compartir: también abre los NO_LISTADO. */
    @GetMapping("/t/{enlaceToken}")
    public ResponseEntity<TorneoPublicoDTO> porEnlace(@PathVariable String enlaceToken) {
        return ResponseEntity.ok(torneoPublicoService.porEnlace(enlaceToken));
    }
}

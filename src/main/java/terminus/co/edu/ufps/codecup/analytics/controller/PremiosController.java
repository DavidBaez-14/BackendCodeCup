package terminus.co.edu.ufps.codecup.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.analytics.dto.PremioPublicoDTO;
import terminus.co.edu.ufps.codecup.analytics.service.CompeticionDatosService;

import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.identidad.api.PersonaResumen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/torneos")
@RequiredArgsConstructor
public class PremiosController {

    private final CompeticionDatosService datos;
    private final DirectorioPersonas directorioPersonas;

    @GetMapping("/{torneoId}/premios")
    public ResponseEntity<List<PremioPublicoDTO>> premios(@PathVariable UUID torneoId) {
        var origen = datos.obtenerPremios(torneoId);
        List<PremioPublicoDTO> result = new ArrayList<>();
        int orden = 1;
        for (var p : origen) {
            result.add(PremioPublicoDTO.builder()
                    .categoria(p.getNombreCatalogo())
                    .alcance(p.getCodigoCatalogo())
                    .nombre(p.getTitulo())
                    .monto(p.getMonto() == null ? 0 : p.getMonto())
                    .orden(orden++)
                    .ganador(buildGanador(p))
                    .build());
        }
        return ResponseEntity.ok(result);
    }

    private PremioPublicoDTO.GanadorDTO buildGanador(
            terminus.co.edu.ufps.codecup.competicion.dto.admin.PremioDTO p) {
        if (p.getGanadorPersonaId() == null && p.getGanadorEquipoTorneoId() == null) return null;
        return PremioPublicoDTO.GanadorDTO.builder()
                .personaId(p.getGanadorPersonaId())
                .nombre(p.getGanadorPersonaId() == null ? null : directorioPersonas.buscar(p.getGanadorPersonaId())
                        .map(PersonaResumen::nombreVisible).orElse(null))
                .equipoNombre(p.getGanadorEquipoNombre())
                .build();
    }
}

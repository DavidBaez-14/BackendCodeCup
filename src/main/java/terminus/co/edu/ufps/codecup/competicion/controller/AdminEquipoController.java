package terminus.co.edu.ufps.codecup.competicion.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.codecup.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.codecup.competicion.service.DelegadoService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
public class AdminEquipoController {

    private final DelegadoService delegadoService;

    @GetMapping("/equipo-torneo/{equipoTorneoId}/miembros")
    public ResponseEntity<List<MiembroEquipoDTO>> miembros(@PathVariable UUID equipoTorneoId) {
        return ResponseEntity.ok(delegadoService.miembrosAdmin(equipoTorneoId));
    }
}

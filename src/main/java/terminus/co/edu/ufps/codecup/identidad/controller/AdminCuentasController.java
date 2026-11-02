package terminus.co.edu.ufps.codecup.identidad.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.codecup.identidad.dto.CuentaAdminDTO;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.service.AdminCuentasService;

@RestController
@RequestMapping("/api/admin/cuentas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminCuentasController {

    private final AdminCuentasService adminCuentasService;

    @GetMapping
    public ResponseEntity<Page<CuentaAdminDTO>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Rol rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminCuentasService.listar(q, rol, page, size));
    }
}

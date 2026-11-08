package terminus.co.edu.ufps.codecup.identidad.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.dto.CuentaAdminDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.RolEstadoDTO;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;

/** Listado de cuentas del panel de admin. Crear cuentas pasó a invitaciones (InvitacionService). */
@Service
@RequiredArgsConstructor
public class AdminCuentasService {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;

    @Transactional(readOnly = true)
    public Page<CuentaAdminDTO> listar(String q, Rol rol, int page, int size) {
        String filtro = q == null || q.isBlank() ? null : q.trim();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        return cuentaRepository.buscar(filtro, rol, pageable).map(this::toDTO);
    }

    private CuentaAdminDTO toDTO(Cuenta cuenta) {
        var persona = cuenta.getPersona();
        var roles = cuentaRolRepository.findByCuentaId(cuenta.getId()).stream()
                .map(cr -> RolEstadoDTO.builder()
                        .id(cr.getId())
                        .rol(cr.getRol().name())
                        .estado(cr.getEstado().name())
                        .fechaSolicitud(cr.getFechaSolicitud())
                        .fechaResolucion(cr.getFechaResolucion())
                        .build())
                .toList();
        return CuentaAdminDTO.builder()
                .id(cuenta.getId())
                .numeroDocumento(persona != null ? persona.getNumeroDocumento() : null)
                .nombre(persona != null ? persona.nombreCompleto() : null)
                .correo(cuenta.getCorreo())
                .estado(cuenta.getEstado().name())
                .fechaCreacion(cuenta.getFechaCreacion())
                .roles(roles)
                .build();
    }
}
